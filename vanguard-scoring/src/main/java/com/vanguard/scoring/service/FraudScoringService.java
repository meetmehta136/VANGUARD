package com.vanguard.scoring.service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.vanguard.common.ModelConstants;
import com.vanguard.common.ScoredTransaction;
import com.vanguard.common.Transaction;
import com.vanguard.scoring.config.ScoringMetrics;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.Collections;

@Service
public class FraudScoringService {

    private static final Logger log = LoggerFactory.getLogger(FraudScoringService.class);

    private final OrtEnvironment ortEnv;
    private final ScoringMetrics scoringMetrics;
    private OrtSession session;

    @Value("classpath:models/fraud_model.onnx")
    private Resource modelResource;

    public FraudScoringService(ScoringMetrics scoringMetrics) {
        this.ortEnv = OrtEnvironment.getEnvironment();
        this.scoringMetrics = scoringMetrics;
    }

    @PostConstruct
    public void init() {
        try (InputStream is = modelResource.getInputStream()) {
            byte[] modelBytes = is.readAllBytes();
            this.session = ortEnv.createSession(modelBytes, new OrtSession.SessionOptions());
            log.info("Fraud model loaded: input={}, output={}",
                session.getInputNames(), session.getOutputNames());
        } catch (IOException | OrtException e) {
            throw new RuntimeException("Failed to load fraud model", e);
        }
    }

    public ScoredTransaction score(Transaction txn, float[] featureVector) {
        long startNs = System.nanoTime();
        float fraudScore = runInference(featureVector);
        long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
        boolean isHighRisk = fraudScore > ModelConstants.FRAUD_HIGH_RISK_THRESHOLD;

        scoringMetrics.incrementScored();
        if (isHighRisk) {
            scoringMetrics.incrementHighRisk();
        }

        log.info("Scored txn {} → {} ({}ms)",
            txn.transactionId(), fraudScore, latencyMs);

        return new ScoredTransaction(txn, fraudScore, isHighRisk, latencyMs);
    }

    public float score(float[] features) {
        if (features.length != ModelConstants.FRAUD_FEATURE_COUNT) {
            throw new IllegalArgumentException(
                "Expected " + ModelConstants.FRAUD_FEATURE_COUNT + " features, got " + features.length);
        }
        return runInference(features);
    }

    private float runInference(float[] features) {
        long startNs = System.nanoTime();
        try (OnnxTensor inputTensor = OnnxTensor.createTensor(
                ortEnv, FloatBuffer.wrap(features), new long[]{1, ModelConstants.FRAUD_FEATURE_COUNT})) {

            OrtSession.Result result = session.run(
                Collections.singletonMap(ModelConstants.FRAUD_MODEL_INPUT_NAME, inputTensor));

            OnnxValue onnxValue = result.get(ModelConstants.FRAUD_MODEL_OUTPUT_NAME)
                .orElseThrow(() -> new RuntimeException("No output for " + ModelConstants.FRAUD_MODEL_OUTPUT_NAME));
            float[][] output = (float[][]) onnxValue.getValue();
            long latencyUs = (System.nanoTime() - startNs) / 1000;
            log.debug("Scored in {} us", latencyUs);
            return output[0][ModelConstants.FRAUD_PROB_INDEX];

        } catch (OrtException e) {
            scoringMetrics.incrementError();
            throw new RuntimeException("ONNX inference failed", e);
        }
    }

    public void reloadModel() {
        OrtSession oldSession = this.session;
        try (InputStream is = modelResource.getInputStream()) {
            byte[] modelBytes = is.readAllBytes();
            this.session = ortEnv.createSession(modelBytes, new OrtSession.SessionOptions());
            if (oldSession != null) {
                oldSession.close();
            }
            log.info("Fraud model hot-reloaded");
        } catch (IOException | OrtException e) {
            throw new RuntimeException("Failed to reload fraud model", e);
        }
    }
}

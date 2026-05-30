package com.vanguard.limiter.service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.vanguard.common.ModelConstants;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;

@Service
public class TrafficForecaster {

    private static final Logger log = LoggerFactory.getLogger(TrafficForecaster.class);

    private final OrtEnvironment ortEnv;
    private OrtSession session;
    private final Deque<Float> recentTraffic = new ArrayDeque<>();

    @Value("classpath:models/traffic_lstm.onnx")
    private Resource modelResource;

    public TrafficForecaster() {
        this.ortEnv = OrtEnvironment.getEnvironment();
    }

    @PostConstruct
    public void init() {
        try (InputStream is = modelResource.getInputStream()) {
            byte[] modelBytes = is.readAllBytes();
            this.session = ortEnv.createSession(modelBytes, new OrtSession.SessionOptions());
            log.info("TrafficForecaster initialized — LSTM loaded: input={}, output={}",
                session.getInputNames(), session.getOutputNames());
        } catch (IOException | OrtException e) {
            throw new RuntimeException("Failed to load traffic LSTM model", e);
        }
    }

    public void recordTraffic(long requestCount) {
        float normalized = (float) ((double) requestCount - ModelConstants.LSTM_MEAN) / ModelConstants.LSTM_STD;
        recentTraffic.addLast(normalized);
        if (recentTraffic.size() > ModelConstants.LSTM_LOOKBACK) {
            recentTraffic.removeFirst();
        }
    }

    @Scheduled(fixedRate = 300000)
    public void scheduledForecast() {
        float predicted = forecastNextTraffic();
        if (predicted > 500) {
            log.warn("Traffic spike predicted: {} requests", String.format("%.1f", predicted));
        } else if (predicted < 100) {
            log.info("Low traffic predicted: {} requests", String.format("%.1f", predicted));
        }
    }

    public float forecastNextTraffic() {
        if (recentTraffic.size() < ModelConstants.LSTM_LOOKBACK) {
            return ModelConstants.LSTM_MEAN;
        }

        float[] sequence = new float[ModelConstants.LSTM_LOOKBACK];
        int i = 0;
        for (Float val : recentTraffic) {
            sequence[i++] = val;
        }

        try (OnnxTensor inputTensor = OnnxTensor.createTensor(
                ortEnv, FloatBuffer.wrap(sequence),
                new long[]{1, ModelConstants.LSTM_LOOKBACK, 1})) {

            OrtSession.Result result = session.run(
                Collections.singletonMap(ModelConstants.LSTM_INPUT_NAME, inputTensor));

            float[][] output = (float[][]) result.get(ModelConstants.LSTM_OUTPUT_NAME)
                .orElseThrow(() -> new RuntimeException("No LSTM output"))
                .getValue();
            float denormalized = output[0][0] * ModelConstants.LSTM_STD + ModelConstants.LSTM_MEAN;
            return Math.max(0, denormalized);

        } catch (OrtException e) {
            log.error("Traffic LSTM inference failed: {}", e.getMessage());
            return ModelConstants.LSTM_MEAN;
        }
    }
}

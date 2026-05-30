package com.vanguard.scoring;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.vanguard.common.ModelConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class OnnxVerificationTest {

    private static OrtEnvironment ortEnv;

    @BeforeAll
    static void init() {
        ortEnv = OrtEnvironment.getEnvironment();
    }

    @Test
    void fraudModelLoadsAndInfers() throws Exception {
        Resource resource = new ClassPathResource("models/fraud_model.onnx");
        assertTrue(resource.exists(), "fraud_model.onnx not found");

        byte[] modelBytes;
        try (InputStream is = resource.getInputStream()) {
            modelBytes = is.readAllBytes();
        }
        assertNotNull(modelBytes);
        assertTrue(modelBytes.length > 0);

        try (OrtSession session = ortEnv.createSession(modelBytes, new OrtSession.SessionOptions())) {
            assertTrue(session.getInputNames().contains(ModelConstants.FRAUD_MODEL_INPUT_NAME),
                "Input name mismatch: " + session.getInputNames());
            assertTrue(session.getOutputNames().contains(ModelConstants.FRAUD_MODEL_OUTPUT_NAME),
                "Output name mismatch: " + session.getOutputNames());

            float[] dummyFeatures = new float[ModelConstants.FRAUD_FEATURE_COUNT];
            for (int i = 0; i < dummyFeatures.length; i++) {
                dummyFeatures[i] = (float) Math.sin(i);
            }

            try (OnnxTensor inputTensor = OnnxTensor.createTensor(
                    ortEnv, FloatBuffer.wrap(dummyFeatures),
                    new long[]{1, ModelConstants.FRAUD_FEATURE_COUNT})) {

                OrtSession.Result result = session.run(
                    Collections.singletonMap(ModelConstants.FRAUD_MODEL_INPUT_NAME, inputTensor));

                float[][] output = (float[][]) result.get(ModelConstants.FRAUD_MODEL_OUTPUT_NAME)
                    .orElseThrow(() -> new RuntimeException("No output"))
                    .getValue();
                assertNotNull(output);
                assertEquals(1, output.length);
                assertEquals(2, output[0].length);

                float fraudProb = output[0][ModelConstants.FRAUD_PROB_INDEX];
                assertTrue(fraudProb >= 0.0f && fraudProb <= 1.0f,
                    "Fraud probability out of range: " + fraudProb);
                System.out.println("Fraud model OK. Input=" + session.getInputNames()
                    + " Output=" + session.getOutputNames()
                    + " Dummy fraud prob=" + fraudProb);
            }
        }
    }

    @Test
    void trafficModelLoadsAndInfers() throws Exception {
        Resource resource = new ClassPathResource("models/traffic_lstm.onnx");
        assertTrue(resource.exists(), "traffic_lstm.onnx not found");

        byte[] modelBytes;
        try (InputStream is = resource.getInputStream()) {
            modelBytes = is.readAllBytes();
        }
        assertNotNull(modelBytes);
        assertTrue(modelBytes.length > 0);

        try (OrtSession session = ortEnv.createSession(modelBytes, new OrtSession.SessionOptions())) {
            assertTrue(session.getInputNames().contains(ModelConstants.LSTM_INPUT_NAME),
                "Input name mismatch: " + session.getInputNames());
            assertTrue(session.getOutputNames().contains(ModelConstants.LSTM_OUTPUT_NAME),
                "Output name mismatch: " + session.getOutputNames());

            float[] dummySequence = new float[ModelConstants.LSTM_LOOKBACK];
            for (int i = 0; i < dummySequence.length; i++) {
                dummySequence[i] = (float) (Math.sin(i * 0.1) * 2.0);
            }

            try (OnnxTensor inputTensor = OnnxTensor.createTensor(
                    ortEnv, FloatBuffer.wrap(dummySequence),
                    new long[]{1, ModelConstants.LSTM_LOOKBACK, 1})) {

                OrtSession.Result result = session.run(
                    Collections.singletonMap(ModelConstants.LSTM_INPUT_NAME, inputTensor));

                float[][] output = (float[][]) result.get(ModelConstants.LSTM_OUTPUT_NAME)
                    .orElseThrow(() -> new RuntimeException("No LSTM output"))
                    .getValue();
                assertNotNull(output);
                assertEquals(1, output.length);
                assertEquals(1, output[0].length);
                System.out.println("Traffic LSTM OK. Input=" + session.getInputNames()
                    + " Output=" + session.getOutputNames()
                    + " Predicted=" + output[0][0]);
            }
        }
    }

    @Test
    void modelConstantsAreCorrect() {
        assertEquals(22, ModelConstants.FRAUD_FEATURE_COUNT);
        assertEquals("float_input", ModelConstants.FRAUD_MODEL_INPUT_NAME);
        assertEquals("probabilities", ModelConstants.FRAUD_MODEL_OUTPUT_NAME);
        assertEquals(1, ModelConstants.FRAUD_PROB_INDEX);
        assertEquals("traffic_sequence", ModelConstants.LSTM_INPUT_NAME);
        assertEquals("predicted_traffic", ModelConstants.LSTM_OUTPUT_NAME);
        assertEquals(60, ModelConstants.LSTM_LOOKBACK);
    }
}

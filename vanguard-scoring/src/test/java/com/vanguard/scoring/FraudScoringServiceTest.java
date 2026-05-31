package com.vanguard.scoring;

import com.vanguard.common.ModelConstants;
import com.vanguard.scoring.config.ScoringMetrics;
import com.vanguard.scoring.service.FraudScoringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class FraudScoringServiceTest {

    @Mock
    private ScoringMetrics scoringMetrics;

    private FraudScoringService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new FraudScoringService(scoringMetrics);
        var field = FraudScoringService.class.getDeclaredField("modelResource");
        field.setAccessible(true);
        field.set(service, new ClassPathResource("models/fraud_model.onnx"));
        service.init();
    }

    @Test
    void fraudScoreIsBetweenZeroAndOne() {
        float[] features = new float[ModelConstants.FRAUD_FEATURE_COUNT];
        float score = service.score(features);
        assertTrue(score >= 0.0f && score <= 1.0f,
            "Score must be between 0 and 1, was: " + score);
    }

    @Test
    void highRiskThresholdIsEightPercent() {
        assertEquals(0.08f, ModelConstants.FRAUD_HIGH_RISK_THRESHOLD);
        assertEquals(22, ModelConstants.FRAUD_FEATURE_COUNT);
    }
}

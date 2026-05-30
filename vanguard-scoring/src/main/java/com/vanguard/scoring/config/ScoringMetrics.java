package com.vanguard.scoring.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class ScoringMetrics {

    private final Timer scoringLatency;
    private final Counter transactionsScored;
    private final Counter highRiskAlerts;
    private final Counter scoringErrors;

    public ScoringMetrics(MeterRegistry registry) {
        this.scoringLatency = Timer.builder("vanguard.scoring.latency")
            .description("ONNX inference latency")
            .register(registry);
        this.transactionsScored = Counter.builder("vanguard.scoring.scored.total")
            .description("Total transactions scored")
            .register(registry);
        this.highRiskAlerts = Counter.builder("vanguard.scoring.high.risk.total")
            .description("High-risk alerts generated")
            .register(registry);
        this.scoringErrors = Counter.builder("vanguard.scoring.errors.total")
            .description("ONNX inference errors")
            .register(registry);
    }

    public Timer.Sample startTimer() {
        return Timer.start();
    }

    public void recordLatency(Timer.Sample sample) {
        sample.stop(scoringLatency);
    }

    public void incrementScored() {
        transactionsScored.increment();
    }

    public void incrementHighRisk() {
        highRiskAlerts.increment();
    }

    public void incrementError() {
        scoringErrors.increment();
    }
}

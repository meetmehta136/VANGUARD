package com.vanguard.ingest.service;

import com.vanguard.common.KafkaTopics;
import com.vanguard.common.TrafficMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class TrafficMetricPublisher {

    private static final Logger log = LoggerFactory.getLogger(TrafficMetricPublisher.class);

    private final KafkaTemplate<String, TrafficMetric> kafkaTemplate;
    private final AtomicLong requestCount = new AtomicLong(0);
    private volatile String currentWindow;

    public TrafficMetricPublisher(KafkaTemplate<String, TrafficMetric> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.currentWindow = windowFor(Instant.now());
    }

    public void recordRequest() {
        String now = windowFor(Instant.now());
        if (!now.equals(currentWindow)) {
            String prevWindow = currentWindow;
            long count = requestCount.getAndSet(0);
            currentWindow = now;
            if (count > 0) {
                TrafficMetric metric = new TrafficMetric(Instant.now(), count, prevWindow);
                kafkaTemplate.send(KafkaTopics.TRAFFIC_METRICS, prevWindow, metric)
                    .thenAccept(result -> log.info("Published traffic metric: window={}, count={}", prevWindow, count))
                    .exceptionally(ex -> {
                        log.error("Failed to publish traffic metric: {}", ex.getMessage());
                        return null;
                    });
            }
        }
        requestCount.incrementAndGet();
    }

    private static String windowFor(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MINUTES).toString();
    }
}

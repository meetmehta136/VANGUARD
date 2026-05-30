package com.vanguard.limiter.service;

import com.vanguard.common.KafkaTopics;
import com.vanguard.common.TrafficMetric;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class TrafficMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(TrafficMetricsCollector.class);

    private final TrafficForecaster trafficForecaster;

    public TrafficMetricsCollector(TrafficForecaster trafficForecaster) {
        this.trafficForecaster = trafficForecaster;
    }

    @KafkaListener(topics = KafkaTopics.TRAFFIC_METRICS, groupId = "vanguard-limiter")
    public void onTrafficMetric(ConsumerRecord<String, TrafficMetric> record) {
        TrafficMetric metric = record.value();
        trafficForecaster.recordTraffic(metric.requestCount());
        log.info("Traffic metric consumed: window={}, count={}", metric.windowMinute(), metric.requestCount());
    }
}

package com.vanguard.common;

public final class KafkaTopics {
    public static final String TXN_RAW = "txn-raw";
    public static final String TXN_SCORED = "txn-scored";
    public static final String TXN_ALERTS = "txn-alerts";
    public static final String TRAFFIC_METRICS = "traffic-metrics";

    private KafkaTopics() {}
}

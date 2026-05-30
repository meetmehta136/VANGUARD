package com.vanguard.common;

public final class ModelConstants {
    public static final int FRAUD_FEATURE_COUNT = 22;
    public static final String FRAUD_MODEL_INPUT_NAME = "float_input";
    public static final String FRAUD_MODEL_OUTPUT_NAME = "probabilities";
    public static final int FRAUD_PROB_INDEX = 1;

    public static final String LSTM_INPUT_NAME = "traffic_sequence";
    public static final String LSTM_OUTPUT_NAME = "predicted_traffic";
    public static final int LSTM_LOOKBACK = 60;
    public static final float LSTM_MEAN = 111.2f;
    public static final float LSTM_STD = 86.4f;

    public static final float FRAUD_HIGH_RISK_THRESHOLD = 0.75f;
    public static final float FRAUD_ALERT_THRESHOLD = 0.85f;

    public static final String REDIS_FEATURES_PREFIX = "features:user:";
    public static final String REDIS_IDEMPOTENCY_PREFIX = "idempotency:txn:";
    public static final String REDIS_RATE_LIMIT_PREFIX = "ratelimit:user:";
    public static final int REDIS_IDEMPOTENCY_TTL_SEC = 3600;
    public static final int REDIS_FEATURES_TTL_SEC = 7200;

    private ModelConstants() {}
}

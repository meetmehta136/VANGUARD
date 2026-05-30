package com.vanguard.common;

import java.time.Instant;

public record TrafficMetric(
    Instant timestamp,
    long requestCount,
    String windowMinute
) {}

package com.vanguard.common;

import java.math.BigDecimal;
import java.time.Instant;

public record Transaction(
    String transactionId,
    String userId,
    BigDecimal amount,
    BigDecimal oldBalanceOrig,
    BigDecimal newBalanceOrig,
    BigDecimal oldBalanceDest,
    BigDecimal newBalanceDest,
    String transactionType,
    String merchantId,
    String merchantCategory,
    Double latitude,
    Double longitude,
    String deviceId,
    String ipAddress,
    String currency,
    Instant timestamp
) {}

package com.vanguard.ingest.dto;

import com.vanguard.common.Transaction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;

public record TransactionRequest(
    @NotBlank String transactionId,
    @NotBlank String userId,
    @Positive BigDecimal amount,
    BigDecimal oldBalanceOrig,
    BigDecimal newBalanceOrig,
    BigDecimal oldBalanceDest,
    BigDecimal newBalanceDest,
    @NotBlank String transactionType,
    String merchantId,
    String merchantCategory,
    Double latitude,
    Double longitude,
    String deviceId,
    String ipAddress,
    String currency
) {
    public Transaction toTransaction() {
        return new Transaction(
            transactionId, userId, amount,
            oldBalanceOrig != null ? oldBalanceOrig : BigDecimal.ZERO,
            newBalanceOrig != null ? newBalanceOrig : BigDecimal.ZERO,
            oldBalanceDest != null ? oldBalanceDest : BigDecimal.ZERO,
            newBalanceDest != null ? newBalanceDest : BigDecimal.ZERO,
            transactionType,
            merchantId, merchantCategory,
            latitude, longitude, deviceId, ipAddress,
            currency != null ? currency : "USD", Instant.now()
        );
    }
}

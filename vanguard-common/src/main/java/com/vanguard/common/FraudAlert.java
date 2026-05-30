package com.vanguard.common;

import java.time.Instant;

public record FraudAlert(
    String transactionId,
    String userId,
    float fraudScore,
    String maskedAmount,
    Instant timestamp
) {
    public static FraudAlert from(Transaction txn, float score) {
        String amount = txn.amount().toString();
        String masked = amount.length() >= 2
            ? "****" + amount.substring(amount.length() - 2)
            : "****";
        return new FraudAlert(
            txn.transactionId(),
            txn.userId(),
            score,
            masked,
            Instant.now()
        );
    }
}

package com.vanguard.ingest.dto;

public record IngestResponse(
    String transactionId,
    String status,
    String message
) {
    public static IngestResponse accepted(String txnId) {
        return new IngestResponse(txnId, "ACCEPTED", "Transaction queued for scoring");
    }

    public static IngestResponse alreadyProcessed(String txnId) {
        return new IngestResponse(txnId, "ALREADY_PROCESSED", "Duplicate transaction ignored");
    }

    public static IngestResponse rejected(String txnId, String reason) {
        return new IngestResponse(txnId, "REJECTED", reason);
    }
}

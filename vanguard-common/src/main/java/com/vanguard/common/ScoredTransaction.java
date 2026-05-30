package com.vanguard.common;

public record ScoredTransaction(
    Transaction transaction,
    float fraudScore,
    boolean isHighRisk,
    long scoringLatencyMs
) {}

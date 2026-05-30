package com.vanguard.ingest.repository;

import com.vanguard.ingest.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, String> {

    @Modifying
    @Query("UPDATE TransactionEntity t SET " +
           "t.fraudScore = :score, " +
           "t.isHighRisk = :highRisk, " +
           "t.status = :status, " +
           "t.scoringLatencyMs = :latency, " +
           "t.scoredAt = CURRENT_TIMESTAMP " +
           "WHERE t.transactionId = :txnId")
    void updateScoringResult(
            @Param("txnId") String txnId,
            @Param("score") float score,
            @Param("highRisk") boolean highRisk,
            @Param("status") String status,
            @Param("latency") long latency
    );
}

package com.vanguard.ingest.consumer;

import com.vanguard.common.KafkaTopics;
import com.vanguard.common.ScoredTransaction;
import com.vanguard.ingest.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TransactionScoringConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionScoringConsumer.class);

    private final TransactionRepository transactionRepository;

    public TransactionScoringConsumer(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    @KafkaListener(
        topics = KafkaTopics.TXN_SCORED,
        groupId = "scoring-db-writer",
        concurrency = "3"
    )
    public void onScoredTransaction(ScoredTransaction scored, Acknowledgment ack) {
        try {
            String status = scored.isHighRisk() ? "HIGH_RISK" : "SCORED";
            transactionRepository.updateScoringResult(
                scored.transaction().transactionId(),
                scored.fraudScore(),
                scored.isHighRisk(),
                status,
                scored.scoringLatencyMs()
            );
            log.info("DB updated for txn {}: score={} highRisk={}",
                scored.transaction().transactionId(), scored.fraudScore(), scored.isHighRisk());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to update DB for txn {}: {}",
                scored.transaction().transactionId(), e.getMessage());
        }
    }
}

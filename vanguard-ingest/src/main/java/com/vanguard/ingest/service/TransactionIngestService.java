package com.vanguard.ingest.service;

import com.vanguard.common.KafkaTopics;
import com.vanguard.common.Transaction;
import com.vanguard.ingest.dto.IngestResponse;
import com.vanguard.ingest.dto.TransactionRequest;
import com.vanguard.ingest.entity.TransactionEntity;
import com.vanguard.ingest.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

@Service
public class TransactionIngestService {

    private static final Logger log = LoggerFactory.getLogger(TransactionIngestService.class);

    private final IdempotencyService idempotencyService;
    private final TransactionRepository repository;
    private final KafkaTemplate<String, Transaction> kafkaTemplate;
    private final TrafficMetricPublisher trafficMetricPublisher;

    public TransactionIngestService(IdempotencyService idempotencyService,
                                    TransactionRepository repository,
                                    KafkaTemplate<String, Transaction> kafkaTemplate,
                                    TrafficMetricPublisher trafficMetricPublisher) {
        this.idempotencyService = idempotencyService;
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.trafficMetricPublisher = trafficMetricPublisher;
    }

    @Transactional
    public IngestResponse ingest(TransactionRequest request) {
        trafficMetricPublisher.recordRequest();
        String txnId = request.transactionId();

        if (idempotencyService.isAlreadyProcessed(txnId)) {
            log.info("Duplicate transaction ignored: {}", txnId);
            return IngestResponse.alreadyProcessed(txnId);
        }

        Transaction transaction = request.toTransaction();
        TransactionEntity entity = new TransactionEntity(
            txnId, request.userId(), request.amount(),
            request.oldBalanceOrig(), request.newBalanceOrig(),
            request.oldBalanceDest(), request.newBalanceDest(),
            request.transactionType(),
            request.merchantId(), request.merchantCategory(),
            request.latitude(), request.longitude(),
            request.deviceId(), request.ipAddress(),
            request.currency(), transaction.timestamp()
        );

        repository.save(entity);
        idempotencyService.markProcessed(txnId);

        CompletableFuture<Void> future = kafkaTemplate.send(
            KafkaTopics.TXN_RAW, txnId, transaction
        ).thenAccept(result -> {
            entity.setStatus("SENT");
            repository.save(entity);
            log.debug("Published transaction {} to Kafka", txnId);
        }).exceptionally(ex -> {
            log.error("Failed to publish transaction {} to Kafka: {}", txnId, ex.getMessage());
            entity.setStatus("KAFKA_FAILED");
            repository.save(entity);
            return null;
        });

        return IngestResponse.accepted(txnId);
    }
}

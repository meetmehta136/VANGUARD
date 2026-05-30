package com.vanguard.scoring.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vanguard.common.*;
import com.vanguard.scoring.service.FeatureExtractionService;
import com.vanguard.scoring.service.FraudScoringService;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.Stores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class FeatureComputationTopology {

    private static final Logger log = LoggerFactory.getLogger(FeatureComputationTopology.class);

    private final ObjectMapper objectMapper;
    private final FeatureExtractionService featureExtractionService;
    private final FraudScoringService fraudScoringService;
    private final StringRedisTemplate redisTemplate;

    public FeatureComputationTopology(
            FeatureExtractionService featureExtractionService,
            FraudScoringService fraudScoringService,
            StringRedisTemplate redisTemplate) {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.featureExtractionService = featureExtractionService;
        this.fraudScoringService = fraudScoringService;
        this.redisTemplate = redisTemplate;
    }

    @Autowired
    public void buildTopology(StreamsBuilder builder) {
        Serde<Transaction> txnSerde = new JsonSerde<>(Transaction.class, objectMapper);
        Serde<String> stringSerde = Serdes.String();
        Serde<VelocityAggregate> velocitySerde = new JsonSerde<>(VelocityAggregate.class, objectMapper);
        Serde<ScoredTransaction> scoredSerde = new JsonSerde<>(ScoredTransaction.class, objectMapper);
        Serde<FraudAlert> alertSerde = new JsonSerde<>(FraudAlert.class, objectMapper);

        KStream<String, Transaction> rawStream = builder.stream(
            KafkaTopics.TXN_RAW,
            Consumed.with(stringSerde, txnSerde)
        );

        KStream<String, Transaction> rekeyed = rawStream
            .map((key, txn) -> KeyValue.pair(txn.userId(), txn));

        KStream<String, ScoredTransaction> scoredStream = rekeyed.map((userId, txn) -> {
            try {
                float[] features = featureExtractionService.toFeatureArray(txn);
                ScoredTransaction scored = fraudScoringService.score(txn, features);
                return KeyValue.pair(userId, scored);
            } catch (Exception e) {
                log.error("Scoring failed for txn {}", txn.transactionId(), e);
                return null;
            }
        }).filter((userId, scored) -> scored != null);

        scoredStream.to(KafkaTopics.TXN_SCORED, Produced.with(stringSerde, scoredSerde));

        scoredStream.filter((userId, scored) -> scored.isHighRisk())
            .peek((userId, scored) -> {
                try {
                    String key = ModelConstants.REDIS_RATE_LIMIT_PREFIX + scored.transaction().userId();
                    redisTemplate.opsForValue().set(
                        key + ":override", "1",
                        Duration.ofMinutes(10)
                    );
                    log.warn("HIGH_RISK: tightened rate limit for user {}",
                        scored.transaction().userId());
                } catch (Exception e) {
                    log.error("Failed to set rate limit override for user {}: {}",
                        scored.transaction().userId(), e.getMessage());
                }
            })
            .map((userId, scored) -> KeyValue.pair(
                userId,
                FraudAlert.from(scored.transaction(), scored.fraudScore())
            ))
            .to(KafkaTopics.TXN_ALERTS, Produced.with(stringSerde, alertSerde));

        KGroupedStream<String, Transaction> groupedByUser = rekeyed
            .groupByKey(Grouped.with(stringSerde, txnSerde));

        KTable<Windowed<String>, VelocityAggregate> velocity1m = groupedByUser
            .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(1)))
            .aggregate(
                VelocityAggregate::new,
                (key, txn, agg) -> agg.add(txn),
                Materialized.<String, VelocityAggregate>as(
                        Stores.persistentWindowStore("velocity-1m", Duration.ofMinutes(5),
                            Duration.ofMinutes(1), false))
                    .withKeySerde(stringSerde)
                    .withValueSerde(velocitySerde)
            );

        KTable<Windowed<String>, VelocityAggregate> velocity5m = groupedByUser
            .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(5)))
            .aggregate(
                VelocityAggregate::new,
                (key, txn, agg) -> agg.add(txn),
                Materialized.<String, VelocityAggregate>as(
                        Stores.persistentWindowStore("velocity-5m", Duration.ofMinutes(15),
                            Duration.ofMinutes(5), false))
                    .withKeySerde(stringSerde)
                    .withValueSerde(velocitySerde)
            );
    }
}

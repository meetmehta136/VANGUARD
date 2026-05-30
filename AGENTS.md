# Session Resume: Gate F Complete — Continue from Here

## Last Known Good State
- **Ingest** running on port 8081, **Scoring** running on port 8082, **Limiter** running on port 8083
- Kafka topics: `txn-raw`, `txn-scored`, `txn-alerts`, `traffic-metrics` — all exist
- DB V2 migration applied: scoring columns (`fraud_score`, `is_high_risk`, `scored_at`, etc.)
- **4 transactions scored and persisted** (Gate E ✅)
- **Gate F complete**: Traffic LSTM forecaster wired into live data path via `TrafficMetricPublisher` in ingest

## Commands to Restart Services (if down)
```powershell
# Start ingest (new window)
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd 'C:\Meet''s CS Projects\VANGUARD'; mvn spring-boot:run -pl vanguard-ingest"

# Wait for ingest UP, then start scoring (new window)
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd 'C:\Meet''s CS Projects\VANGUARD'; mvn spring-boot:run -pl vanguard-scoring"

# Start limiter (new window, if needed)
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd 'C:\Meet''s CS Projects\VANGUARD'; mvn spring-boot:run -pl vanguard-limiter"
```

## Files Changed & Why
| File | Change | Reason |
|---|---|---|
| `vanguard-scoring/.../KafkaStreamsConfig.java:37` | `EXACTLY_ONCE_V2` → `AT_LEAST_ONCE` | Broker doesn't support transactions |
| `vanguard-scoring/.../FeatureComputationTopology.java` | Replaced `foreach`+`KafkaTemplate` with `.to()` | Stream output must go through transactional producer |
| `vanguard-ingest/.../TransactionScoringConsumer.java` | Added `@Transactional` | `@Modifying` query needs a transaction |
| `vanguard-ingest/.../application.yml` | Added `spring.kafka.listener.ack-mode: MANUAL_IMMEDIATE` | Needed for `Acknowledgment` parameter injection |
| `vanguard-scoring/.../KafkaStreamsConfig.java` | App ID: `vanguard-scoring-streams-v2` | Clean slate (old changelogs had stale serde data) |
| `vanguard-ingest/.../KafkaProducerConfig.java` | Added `trafficMetricProducerFactory` + `trafficMetricKafkaTemplate` beans | Gate F: Needed to publish traffic metrics to Kafka |
| `vanguard-ingest/.../TrafficMetricPublisher.java` | **New file** | Gate F: Per-minute window traffic aggregation + Kafka publish |
| `vanguard-ingest/.../TransactionIngestService.java` | Inject `TrafficMetricPublisher`, call `recordRequest()` | Gate F: Feed traffic data for LSTM forecaster |
| `vanguard-limiter/.../TrafficMetricsCollector.java` | Simplified: feed forecaster from `@KafkaListener` directly | Gate F: Consume traffic metrics from Kafka |
| `vanguard-scoring/.../KafkaProducerConfig.java` | **Deleted** | Cleanup: `scoredProducer`/`alertProducer` beans unused after topology refactor |

## Key Metrics (last observed)
- `vanguard.scoring.scored.total` = 4, `vanguard.scoring.errors.total` = 0
- DB: 4 rows with `status=SCORED`, `fraud_score` populated, `scored_at` not null
- Max score observed: ~0.081 (well below `FRAUD_HIGH_RISK_THRESHOLD=0.75`)

## Gate F Design: Traffic Data Flow
```
HTTP Request → TransactionIngestService
                → TrafficMetricPublisher.recordRequest()
                  → aggregates per-minute window in memory
                  → publishes TrafficMetric to `traffic-metrics` topic on window rollover
                    → TrafficMetricsCollector (limiter) consumes via @KafkaListener
                      → TrafficForecaster.recordTraffic(count)
                        → LSTM prediction via scheduledForecast() every 5 min
```

## Todo
- [ ] Verify Gate F end-to-end: hit ingest endpoint, check traffic-metrics topic, check limiter logs for LSTM predictions
- [ ] Add `traffic-metrics` topic creation to infrastructure setup if not auto-created

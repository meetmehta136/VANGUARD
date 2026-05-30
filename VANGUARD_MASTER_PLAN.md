# VANGUARD — Master Execution Plan v2.0

**Project:** Production-grade, real-time fraud intelligence platform
**Stack:** Java 21 + Spring Boot 3.3.6 + Kafka Streams + ONNX Runtime + Redis + PostgreSQL
**Package:** `com.vanguard.*`
**Module prefix:** `vanguard-`
**Dataset:** CiferAI Fraud Detection — 1.5M rows, 22 features, 0.13% fraud rate
**Training:** Balanced undersampling (10:1 legit:fraud)

---

## Architecture Overview

```
Client → Ingest(8081) → Kafka → [Streams Feature Computation] → Redis
  → Scoring Service(8082) → ONNX(XGBoost) → Kafka → Alert Gateway(8084) → WebSocket
  → Traffic Metrics → LSTM Forecaster → Rate Limiter(8083) → Redis
```

## Module Structure

```
VANGUARD/
├── ml/
│   ├── fraud/
│   │   ├── data/
│   │   │   ├── train_transaction.csv        (1.5M rows)
│   │   │   ├── processed_features.parquet
│   │   │   └── feature_columns.txt           [22 features]
│   │   ├── models/
│   │   │   ├── fraud_model.json
│   │   │   ├── fraud_model.onnx
│   │   │   └── model_metadata.json
│   │   ├── feature_engineering.py            [✓ DONE]
│   │   ├── train_fraud_model_v2.py           [✓ DONE]
│   │   ├── evaluate_model.py                 [✓ DONE]
│   │   └── export_onnx.py                    [✓ DONE]
│   └── traffic/
│       ├── models/
│       │   ├── traffic_lstm.pt
│       │   ├── traffic_lstm.onnx
│       │   └── traffic_metadata.json
│       ├── train_traffic_lstm.py             [✓ DONE]
│       └── export_lstm_onnx.py               [✓ DONE]
├── vanguard-common/          ← DTOs + constants [✓ pom.xml + Java DTOs]
├── vanguard-ingest/          ← port 8081        [✓ pom.xml, needs Java]
├── vanguard-scoring/         ← port 8082        [✓ pom.xml, needs Java]
├── vanguard-limiter/         ← port 8083        [✓ pom.xml, needs Java]
├── vanguard-alert/           ← port 8084        [✓ pom.xml, needs Java]
├── monitoring/               [✓ prometheus.yml]
├── load-test/                [ ]
├── docker-compose.yml        [✓ DONE]
├── VANGUARD_MASTER_PLAN.md
├── .gitignore                [✓ DONE]
├── README.md                 [✓ DONE]
├── pom.xml (root)            [✓ DONE]
```

---

## [✓] TASK 1 — Python Environment + ML Setup
Folder structure, venv, setup scripts, .gitignore, README. All deps installed.

---

## [✓] TASK 2 — Feature Engineering (CiferAI Dataset)
- 22 active features (3 constant ones dropped: `dest_no_increase`, `orig_had_zero_balance`, `dest_had_zero_balance`)
- One-hot encoded `type`, balance deltas, cyclic time encoding, amount features
- **Feature count: 22**

---

## [✓] TASK 3 — XGBoost + ONNX Export
**Final model:** `train_fraud_model_v2.py` — balanced undersampling (10:1 ratio)
**ROC-AUC: 0.9785 | AUPRC: 0.4101 | Fraud caught: 68.1% | False alarms: 1.32%**

**ONNX constants (hardcoded in ModelConstants.java):**
- `FRAUD_FEATURE_COUNT = 22`
- `FRAUD_MODEL_INPUT_NAME = "float_input"`
- `FRAUD_MODEL_OUTPUT_NAME = "probabilities"` (fraud prob at index [:, 1])

---

## [✓] TASK 4 — LSTM Traffic Model + ONNX Export
**Model:** LSTM(64,2), lookback=60, 60 epochs on synthetic traffic
**ONNX constants:**
- `LSTM_INPUT_NAME = "traffic_sequence"`
- `LSTM_OUTPUT_NAME = "predicted_traffic"`
- `LSTM_MEAN = 111.2f`, `LSTM_STD = 86.4f`

---

## [✓] TASK 5 — Root Maven POM + Common Module (CODE GENERATED)
- Root pom.xml (multi-module, Java 21, Spring Boot 3.3.6)
- All 5 module pom.xml files
- vanguard-common: 8 Java files (Transaction, ScoredTransaction, FraudAlert, TrafficMetric, UserFeatures, VelocityAggregate, KafkaTopics, ModelConstants)
- ONNX models copied to `vanguard-scoring/src/main/resources/models/`

**Verification:** Run `mvn clean compile -pl vanguard-common` (requires Java 21 + Maven)

---

## [ ] TASK 6 — ONNX Java Verification Test
**Not started.** File: `OnnxVerificationTest.java`

---

## [✓] TASK 7 — Docker Compose Infrastructure (CODE GENERATED)
- `docker-compose.yml` with 7 services (postgres, redis, zookeeper, kafka, kafka-ui, prometheus, grafana)
- `monitoring/prometheus.yml`

**Verification:** `docker compose up -d`

---

## [ ] TASK 8 — vanguard-ingest: REST API + Kafka Producer
**Needs:** Flyway migration, TransactionRequest, IngestResponse, TransactionEntity, TransactionRepository, KafkaProducerConfig, TransactionController, IngestResult, application.yml, application class, **TransactionIngestService.java**, **IdempotencyService.java**

---

## [ ] TASK 9 — Kafka Streams Feature Computation
**Needs:** KafkaStreamsConfig, CustomJsonSerde, GeoCalculator, TransactionScoringConsumer, FeatureExtractionService, **FeatureComputationTopology.java**, **RedisFeatureStore.java**

---

## [ ] TASK 10 — ONNX Fraud Scoring Service
**Needs:** ModelReloadEndpoint, ScoringMetrics, **FraudScoringService.java**

---

## [ ] TASK 11 — Alert Gateway + WebSocket
**Needs:** WebSocketConfig, FraudAlertConsumer, AlertDashboardController, dashboard.html, application class

---

## [ ] TASK 12 — Adaptive Rate Limiter
**Needs:** @RateLimited annotation, RateLimiterService, TrafficMetricsCollector, **RateLimitAspect.java**, **TrafficForecaster.java**

---

## [ ] TASK 13 — Resilience4j + Drift Detection
**Needs:** Circuit breaker config, ModelDriftDetector (PSI)

---

## [ ] TASK 14 — Testcontainers Integration Tests
**Not started.**

---

## [ ] TASK 15 — Load Testing + Metrics
**Not started.**

---

## [ ] TASK 16 — GitHub CI + Final Polish
**Not started.**

---

## CRITICAL JAVA CONSTANTS
| Constant | Value | Verified |
|---|---|---|
| FRAUD_FEATURE_COUNT | 22 | ✅ |
| FRAUD_MODEL_INPUT_NAME | `float_input` | ✅ ONNX |
| FRAUD_MODEL_OUTPUT_NAME | `probabilities` | ✅ ONNX |
| FRAUD_PROB_INDEX | 1 | ✅ |
| FRAUD_HIGH_RISK_THRESHOLD | 0.75f | Default |
| FRAUD_ALERT_THRESHOLD | 0.85f | Default |
| LSTM_INPUT_NAME | `traffic_sequence` | ✅ ONNX |
| LSTM_OUTPUT_NAME | `predicted_traffic` | ✅ ONNX |
| LSTM_LOOKBACK | 60 | ✅ |
| LSTM_MEAN | 111.2f | ✅ metadata |
| LSTM_STD | 86.4f | ✅ metadata |
| REDIS_IDEMPOTENCY_TTL | 3600s | Default |
| REDIS_FEATURES_TTL | 7200s | Default |

## PORT ALLOCATION
| Service | Port |
|---|---|
| vanguard-ingest | 8081 |
| vanguard-scoring | 8082 |
| vanguard-limiter | 8083 |
| vanguard-alert | 8084 |
| PostgreSQL | 5432 |
| Redis | 6379 |
| Kafka | 9092 |
| Kafka UI | 8090 |
| Prometheus | 9090 |
| Grafana | 3000 |

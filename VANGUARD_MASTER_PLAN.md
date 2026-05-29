# VANGUARD — Master Execution Plan

**Project:** Production-grade, real-time fraud intelligence platform
**Stack:** Java 21 + Spring Boot 3.3.6 + Kafka Streams + ONNX Runtime + Redis + PostgreSQL
**Package:** `com.vanguard.*`
**Module prefix:** `vanguard-` (vanguard-common, vanguard-ingest, vanguard-scoring, vanguard-limiter, vanguard-alert)

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
│   │   ├── data/              ← Kaggle IEEE-CIS dataset
│   │   ├── models/            ← .onnx files, .json model files
│   │   ├── feature_engineering.py
│   │   ├── train_fraud_model.py
│   │   └── export_onnx.py
│   └── traffic/
│       ├── models/            ← .onnx files, metadata
│       ├── train_traffic_lstm.py
│       └── export_lstm_onnx.py
├── vanguard-common/           ← shared DTOs, constants, serdes
├── vanguard-ingest/           ← port 8081 — REST API + Kafka producer
├── vanguard-scoring/          ← port 8082 — ONNX inference + Streams topology
├── vanguard-limiter/          ← port 8083 — rate limiting + LSTM forecast
├── vanguard-alert/            ← port 8084 — WebSocket alert dashboard
├── monitoring/
├── load-test/
├── docker-compose.yml
├── VANGUARD_MASTER_PLAN.md
├── .gitignore
├── README.md
└── pom.xml
```

---

## TASK 1 — Python Environment + ML Setup

**STEP 1.1** — Create folder structure (all directories above)
**STEP 1.2** — setup.bat / setup.sh for Python venv
**STEP 1.3** — .gitignore + initial README.md

**VERIFICATION:** `python -c "import xgboost, torch, skl2onnx, onnxruntime; print('All ML deps OK')"`

---

## TASK 2 — Fraud Dataset Feature Engineering

**File:** `ml/fraud/feature_engineering.py`
- load_and_merge() on TransactionID
- Drop >80% null columns
- Cyclic encoding (hour, day_of_week sin/cos)
- log_amount, amount_cents
- Frequency encoding for email domains
- LabelEncoder for objects, fillna median
- Output: processed_features.parquet + feature_columns.txt

**VERIFICATION:** Feature count 50-120, files saved

---

## TASK 3 — Train XGBoost + Export ONNX

**Files:**
- `ml/fraud/train_fraud_model.py` — XGBClassifier, SMOTE, AUPRC, F2 threshold
- `ml/fraud/export_onnx.py` — skl2onnx, target_opset=17, verify with onnxruntime

**VERIFICATION:** AUPRC > 0.50, "ONNX VERIFICATION PASSED"

---

## TASK 4 — Train LSTM Traffic Model + Export ONNX

**Files:**
- `ml/traffic/train_traffic_lstm.py` — synthetic traffic, LSTM(64,2), 60 epochs
- `ml/traffic/export_lstm_onnx.py` — dynamic_axes, opset=17

**VERIFICATION:** "LSTM ONNX VERIFICATION PASSED", both .onnx files present

---

## TASK 5 — Root Maven POM + Common Module

**Files:**
- Root pom.xml — multi-module, Java 21, Spring Boot 3.3.6
- vanguard-common/pom.xml
- Transaction.java (record), ScoredTransaction.java, FraudAlert.java
- TrafficMetric.java, VelocityAggregate.java, UserFeatures.java
- KafkaTopics.java, ModelConstants.java
- Copy ONNX models to scoring resources

**VERIFICATION:** `mvn clean compile -pl vanguard-common`

---

## TASK 6 — ONNX Java Verification Test

**File:** `vanguard-scoring/src/test/java/com/vanguard/scoring/OnnxVerificationTest.java`
- Load both ONNX models
- Print input/output names and shapes
- Dummy inference

**VERIFICATION:** "BOTH MODELS VERIFIED" — update ModelConstants if names differ

---

## TASK 7 — Docker Compose Infrastructure

**Services:** postgres, redis, zookeeper, kafka, kafka-ui, prometheus, grafana
**Files:** docker-compose.yml, monitoring/prometheus.yml

**VERIFICATION:** All 7 containers healthy

---

## TASK 8 — vanguard-ingest: REST API + Kafka Producer

**Files:** pom.xml, Flyway migration, TransactionRequest, IngestResponse, TransactionEntity,
TransactionRepository, KafkaProducerConfig, TransactionController, IngestResult, application.yml

**I WRITE:** TransactionIngestService.java, IdempotencyService.java

**VERIFICATION:** POST → 202, duplicate → 200 ALREADY_PROCESSED, Kafka UI shows message

---

## TASK 9 — Kafka Streams Feature Computation

**Files:** application.yml, KafkaStreamsConfig, CustomJsonSerde, VelocityAggregate serde,
GeoCalculator, TransactionScoringConsumer, FeatureExtractionService

**I WRITE:** FeatureComputationTopology.java, RedisFeatureStore.java

**VERIFICATION:** Redis has velocity features, txn-scored topic populated

---

## TASK 10 — ONNX Fraud Scoring Service

**Files:** ModelReloadEndpoint, ScoringMetrics

**I WRITE:** FraudScoringService.java

**VERIFICATION:** Scores return 0.0-1.0, latency < 500ms, hot-reload works

---

## TASK 11 — Alert Gateway + WebSocket

**Files:** pom.xml, WebSocketConfig, FraudAlertConsumer, AlertDashboardController, dashboard.html

**VERIFICATION:** WebSocket dashboard shows alerts for high-risk transactions

---

## TASK 12 — Adaptive Rate Limiter

**Files:** pom.xml, @RateLimited annotation, RateLimiterService (Redis token bucket Lua),
TrafficMetricsCollector, application.yml

**I WRITE:** RateLimitAspect.java, TrafficForecaster.java

**VERIFICATION:** 429 after limit exceeded, LSTM adapts limits

---

## TASK 13 — Resilience4j + Drift Detection

**Files:** Circuit breaker config, RedisFeatureStore updates, ModelDriftDetector (PSI)

**VERIFICATION:** Redis fallback works, PSI logged

---

## TASK 14 — Testcontainers Integration Tests

**File:** ScoringIntegrationTest.java — 5 test methods
**VERIFICATION:** All 5 tests GREEN, coverage > 60%

---

## TASK 15 — Load Testing + Metrics

**Files:** load-test/fraud_load_test.js (k6), monitoring/grafana-dashboard.json

**VERIFICATION:** P99 < 200ms at 150 RPS, Grafana panels live

---

## TASK 16 — GitHub CI + Final Polish

**Files:** .github/workflows/ci.yml, .github/workflows/ml-check.yml, CONTRIBUTING.md

**VERIFICATION:** CI green, 50+ commits, Swagger UI accessible

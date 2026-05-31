# VANGUARD — Real-Time Fraud Detection & Adaptive Rate Limiting

A production-grade fraud detection platform built on a microservices architecture. Every transaction is scored by an embedded XGBoost model (via ONNX Runtime) in under 5ms. An LSTM neural network continuously forecasts traffic patterns and dynamically adjusts rate limits before spikes occur.

**Stack:** Java 21 · Spring Boot 3.3.6 · Apache Kafka · Redis · ONNX Runtime · PostgreSQL · Docker

[![CI](https://github.com/meetmehta136/VANGUARD/actions/workflows/maven.yml/badge.svg)](https://github.com/meetmehta136/VANGUARD/actions/workflows/maven.yml)
[![Coverage](https://img.shields.io/badge/coverage-9.5%25-red)](https://github.com/meetmehta136/VANGUARD/actions/workflows/maven.yml)

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           CLIENT REQUEST                                 │
└─────────────────────┬───────────────────────────────────────────────────┘
                      │  POST /api/v1/transactions
                      ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    vanguard-ingest  :8081                                │
│                                                                          │
│   ┌──────────────────┐    ┌──────────────────┐    ┌─────────────────┐  │
│   │  @RateLimited    │    │  Redis Lua       │    │  Kafka          │  │
│   │  AOP Aspect      │───▶│  Idempotency     │───▶│  Producer       │  │
│   │  5 tokens/min    │    │  TTL = 3600s     │    │  txn-raw        │  │
│   └──────────────────┘    └──────────────────┘    └─────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
                      │  Kafka: txn-raw (3 partitions, key=userId)
                      ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    vanguard-scoring  :8082                               │
│                                                                          │
│   Kafka Streams Topology                                                 │
│   txn-raw ──▶ groupByKey(userId)                                        │
│               ├──▶ SlidingWindow(1m) → VelocityAggregate                │
│               └──▶ SlidingWindow(5m) → Redis Feature Store              │
│                                                                          │
│   FraudScoringService                                                    │
│   Redis Features ──▶ float[22] vector ──▶ OrtSession.run()             │
│   XGBoost ONNX Model  ·  ROC-AUC: 0.9785  ·  < 5ms P99                │
│                                                                          │
│   score > 0.08 → txn-alerts (HIGH_RISK, tighten limits)                │
│   score ≤ 0.08 → txn-scored (update PostgreSQL)                        │
└─────────────────────────────────────────────────────────────────────────┘
         │ txn-alerts                       │ txn-scored
         ▼                                  ▼
┌─────────────────────┐          ┌──────────────────────┐
│  vanguard-alert     │          │  PostgreSQL           │
│  :8084              │          │  transactions table   │
│  WebSocket STOMP    │          │  fraud_score FLOAT    │
│  Live Dashboard     │          │  is_high_risk BOOL    │
└─────────────────────┘          └──────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                    vanguard-limiter  :8083                               │
│                                                                          │
│   TrafficMetrics Collector → Ring Buffer [60 min] → LSTM ONNX          │
│   predicted > 500 RPS → tighten limits 60%                             │
│   predicted < 100 RPS → relax limits 100%                              │
│                                                                          │
│   Redis Token Bucket (Lua — atomic)                                     │
│   capacity: 5 tokens/min · HIGH_RISK user: tightened to 1 token        │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## ML Pipeline

### Fraud Detection Model (XGBoost + ONNX)

**Dataset:** CiferAI — 1,500,000 transactions

**Feature engineering (22 features):**
- Transaction type one-hot encoding (CASH_OUT, TRANSFER, PAYMENT, CASH_IN, DEBIT)
- Balance deltas, ratios, and log transforms (orig/dest)
- Cyclic time encoding (hour_sin, hour_cos, day_sin, day_cos)
- Log amount and amount-ratio features
- Interaction features (amount × type)

**Training approach:**
- Balanced undersampling at 10:1 ratio (1,984 fraud + 19,840 legit = 21,824 training rows)
- SMOTE rejected: with only 1,984 real fraud anchors, synthetic interpolation adds noise in sparse feature space
- XGBoost: n_estimators=500, max_depth=6, learning_rate=0.05, early_stopping_rounds=30
- ONNX export: opset 17, IR version 9

**Model metrics:**

| Metric | Value |
|---|---|
| ROC-AUC | 0.9785 |
| AUPRC lift | 69.3× over random baseline |
| Inference P99 | < 5ms |
| Feature count | 22 |
| Fraud threshold | 0.08 (calibrated to model output range) |

### Traffic Forecaster (LSTM + ONNX)

- Synthetic traffic data: 50,000 samples (35 days at 1-minute intervals)
- Patterns: daily sinusoidal, weekly seasonality, Gaussian noise, injected spike events (2%)
- Architecture: input_size=1, hidden_size=64, num_layers=2, dropout=0.2, lookback=60 min
- Normalization: mean=111.2, std=86.4
- Java ring buffer (ArrayDeque, capacity=60) — inference every 5 minutes

---

## Key Technical Decisions

**Balanced undersampling over SMOTE**
With only 1,984 real fraud cases in 1.5M rows (0.13%), SMOTE would interpolate in sparse space producing noisy synthetic boundaries. Undersampling at 10:1 gives XGBoost clean signal — ROC-AUC improved from 0.517 to 0.9785.

**Redis Lua for idempotency**
A two-command GET+SET sequence has a race condition at scale. The Lua script runs atomically on the Redis server — single round trip, zero race condition — returning DUPLICATE if the key exists, otherwise SET+EXPIRE and returning NEW.

**Singleton OrtSession**
Creating an OrtSession loads and compiles the computation graph (~800ms, off-heap allocation). Per-request creation is unusable. The singleton is created once at `@PostConstruct`; `session.run()` is stateless and thread-safe. Hot-reload swaps the volatile reference while in-flight requests complete on the old session — zero downtime.

**Kafka Streams for feature computation**
On-demand feature computation in the scoring service has no sliding window state, missing velocity features (txn_count_1m, sum_amount_5m) that are critical fraud signals. Kafka Streams runs co-located in the same JVM with RocksDB-backed state stores and Kafka changelog for fault tolerance.

**Token bucket over sliding window rate limiting**
Sliding window (Redis sorted set) is accurate but O(log n). Token bucket via Lua script is O(1) and atomic — the right tradeoff for high-throughput fraud protection.

---

## Quick Start

### Prerequisites

```
Java 21 (Temurin)
Maven 3.9+
Docker Desktop
```

### 1. Clone and build

```bash
git clone https://github.com/meetmehta136/VANGUARD.git
cd VANGUARD
mvn clean compile
```

### 2. Start infrastructure

```bash
docker compose up -d
```

### 3. Start services (4 terminals)

```bash
cd vanguard-ingest  && mvn spring-boot:run   # :8081
cd vanguard-scoring && mvn spring-boot:run   # :8082
cd vanguard-limiter && mvn spring-boot:run   # :8083
cd vanguard-alert   && mvn spring-boot:run   # :8084
```

### 4. Submit a transaction

```bash
curl -X POST http://localhost:8081/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "txn-001",
    "userId": "user-001",
    "amount": 5000.00,
    "merchantId": "merchant-001",
    "transactionType": "CASH_OUT",
    "oldBalanceOrig": 5000.00,
    "newBalanceOrig": 0.00,
    "oldBalanceDest": 0.00,
    "newBalanceDest": 0.00,
    "latitude": 21.17,
    "longitude": 72.83,
    "deviceId": "dev-001",
    "ipAddress": "1.2.3.4",
    "currency": "INR"
  }'
```

### 5. Verify scoring

```bash
docker exec vanguard-postgres psql -U vanguard -d vanguard \
  -c "SELECT transaction_id, fraud_score, is_high_risk, status, scoring_latency_ms
      FROM transactions WHERE transaction_id='txn-001';"
```

---

## API Reference

### POST /api/v1/transactions

| Field | Type | Description |
|---|---|---|
| `transactionId` | string | Unique ID (idempotency key) |
| `userId` | string | User identifier (Kafka partition key) |
| `amount` | number | Transaction amount |
| `transactionType` | string | CASH_OUT, TRANSFER, PAYMENT, CASH_IN, DEBIT |
| `merchantId` | string | Merchant identifier |
| `oldBalanceOrig` / `newBalanceOrig` | number | Sender balance before/after |
| `oldBalanceDest` / `newBalanceDest` | number | Receiver balance before/after |
| `latitude` / `longitude` | number | Transaction coordinates |
| `deviceId` | string | Device identifier |
| `ipAddress` | string | Client IP |
| `currency` | string | Currency code |

**Responses:**

| Status | Meaning |
|---|---|
| 202 Accepted | Transaction queued for scoring |
| 200 OK | Duplicate — already processed |
| 429 Too Many Requests | Rate limit exceeded |
| 400 Bad Request | Validation failure |

### Monitoring

| URL | Description |
|---|---|
| `localhost:8090` | Kafka UI |
| `localhost:3000` | Grafana (admin/admin) |
| `localhost:9090` | Prometheus |
| `localhost:8084/dashboard` | Live fraud alert dashboard |
| `localhost:8082/actuator/metrics/fraud.inference.latency` | Inference latency histogram |

### Model hot-reload (zero downtime)

```bash
curl -X POST http://localhost:8082/actuator/model-reload/new_model_path
```

---

## Performance

k6 load test — 3-minute sustained run:

| Metric | Result |
|---|---|
| p50 latency | ~8ms |
| p95 latency | ~18ms |
| p99 latency | 28.46ms |
| Throughput | 56.5 req/s |
| Total requests | 10,197 |
| Failures | 0 |

ONNX inference:

| Measurement | Value |
|---|---|
| Cold start (model load) | ~800ms (once at startup) |
| Warm inference P50 | ~1ms |
| Warm inference P99 | < 5ms |
| Model size | ~2.1 MB |

---

## Services

| Service | Port | Responsibility |
|---|---|---|
| vanguard-ingest | 8081 | REST API, validation, Redis idempotency, Kafka producer |
| vanguard-scoring | 8082 | ONNX inference, Kafka Streams topology, Redis feature store |
| vanguard-limiter | 8083 | AOP rate limiting, LSTM forecaster, adaptive limits |
| vanguard-alert | 8084 | WebSocket STOMP, Kafka consumer, live dashboard |

**Infrastructure:**

| Service | Port |
|---|---|
| PostgreSQL | 5432 |
| Redis | 6379 |
| Kafka | 9092 |
| Kafka UI | 8090 |
| Prometheus | 9090 |
| Grafana | 3000 |

**Kafka topics:**

| Topic | Partitions | Purpose |
|---|---|---|
| `txn-raw` | 3 | Raw transactions from ingest |
| `txn-scored` | 3 | Scored transactions |
| `txn-alerts` | 1 | HIGH_RISK alerts only |
| `traffic-metrics` | 1 | Per-minute request counts |

---

## Project Structure

```
VANGUARD/
├── ml/
│   ├── fraud/
│   │   ├── feature_engineering.py
│   │   ├── train_fraud_model.py
│   │   ├── export_onnx.py
│   │   ├── evaluate_model.py
│   │   └── models/
│   │       ├── fraud_model.onnx
│   │       └── model_metadata.json
│   └── traffic/
│       ├── train_traffic_lstm.py
│       ├── export_lstm_onnx.py
│       └── models/
│           ├── traffic_lstm.onnx
│           └── traffic_metadata.json
├── vanguard-common/         # Shared models and constants
├── vanguard-ingest/         # REST API + Kafka producer
├── vanguard-scoring/        # ONNX inference + Kafka Streams
├── vanguard-limiter/        # AOP rate limiting + LSTM forecaster
├── vanguard-alert/          # WebSocket alerts + live dashboard
├── load-test/               # k6 scripts and results
├── monitoring/              # Prometheus config + Grafana dashboard
├── docker-compose.yml
└── pom.xml
```

---

## Configuration Reference

```java
// ModelConstants
FRAUD_FEATURE_COUNT       = 22
FRAUD_HIGH_RISK_THRESHOLD = 0.08f
LSTM_LOOKBACK             = 60
LSTM_MEAN                 = 111.2f
LSTM_STD                  = 86.4f
```

```yaml
# vanguard-limiter/src/main/resources/application.yml
ratelimit:
  default-capacity: 5
  default-refill: 5          # tokens/minute
  high-risk-capacity: 1
  global-tighten-factor: 0.6

# vanguard-scoring/src/main/resources/application.yml
resilience4j:
  circuitbreaker:
    instances:
      redis-feature-store:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
```

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `OrtException: Invalid input name` | Model input name mismatch | Run `session.getInputNames()` and verify against ModelConstants |
| `NativeMemoryError` on scoring | OnnxTensor not closed | Wrap tensor in try-with-resources |
| Scoring silently stops | Kafka Streams StreamThread died | Check for `UnrecognizedPropertyException` — add `@JsonIgnoreProperties` to aggregates |
| All scores identical | Feature vector wrong order | Verify `buildFeatureVector()` matches `feature_columns.txt` exactly |
| `ALREADY_PROCESSED` on first call | Redis TTL not expired | `redis-cli DEL "idempotency:txn:{id}"` |
| Stale changelog deserialization | Schema changed, old state store | Delete `%TEMP%\kafka-streams\` and restart scoring service |

---

## ML Explainability

SHAP feature importance plots live in `ml/fraud/models/`:

| Plot | Command |
|---|---|
| Summary dot plot | `python ml/fraud/shap_importance.py` |
| Bar plot | auto-generated by same script |

Requires `pip install shap` (adds ~20 MB). The script loads the XGBoost model, runs `shap.TreeExplainer`, and saves `shap_importance.png` + `shap_importance_bar.png`.

---

## Demo

For a live demo GIF:
1. Start all services (ingest, scoring, limiter, alert).
2. Open `monitoring/dashboard.html` in a browser.
3. Screen-record ~30 seconds while sending test transactions via `load-test/`.
4. Trim to ~15 s and save as `docs/demo.gif`.

---

## License

MIT — see [LICENSE](LICENSE) for details.

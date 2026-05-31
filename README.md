# VANGUARD — Real-Time Fraud Intelligence Platform

[![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.6-6DB33F?style=flat-square&logo=springboot)](https://spring.io/projects/spring-boot)
[![Kafka](https://img.shields.io/badge/Apache_Kafka-7.6.0-231F20?style=flat-square&logo=apachekafka)](https://kafka.apache.org/)
[![Redis](https://img.shields.io/badge/Redis-7.0-DC382D?style=flat-square&logo=redis)](https://redis.io/)
[![ONNX](https://img.shields.io/badge/ONNX_Runtime-1.17.0-005CED?style=flat-square)](https://onnxruntime.ai/)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)

**Overview** • [Architecture](#architecture) • [ML Pipeline](#ml-pipeline) • [Quick Start](#quick-start) • [API Reference](#api-reference) • [Performance](#performance) • [Project Structure](#project-structure)

---

## Overview

VANGUARD is a production-grade real-time fraud detection and adaptive rate limiting platform built on a microservices architecture. Every financial transaction is scored by an XGBoost model (via ONNX Runtime) in under 5ms. An LSTM neural network continuously forecasts traffic patterns and dynamically adjusts rate limits before traffic spikes materialize.

### Key Design Decisions

| Capability | Implementation |
|---|---|
| **Sub-5ms inference** | ONNX Runtime embedded in JVM — no network hop vs Python microservice |
| **Atomic idempotency** | Redis Lua single round-trip — zero race conditions at scale |
| **Adaptive rate limits** | LSTM forecasts + token bucket — proactive, not reactive |
| **Zero-downtime model swap** | Hot-reload via `volatile` reference swap on OrtSession singleton |
| **Streaming feature computation** | Kafka Streams sliding windows (1m/5m) for real-time velocity features |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          CLIENT REQUEST                                      │
│                    POST /api/v1/transactions                                  │
└───────────────────────────┬─────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ vanguard-ingest  :8081                                                        │
│                                                                               │
│  @RateLimited AOP → Redis Lua Idempotency → Kafka Producer (txn-raw)        │
│  Aspect (5/min)     TTL=3600s             3 partitions, key=userId          │
└─────────────────────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ vanguard-scoring  :8082                                                       │
│                                                                               │
│  Kafka Streams Topology                                                        │
│    ├── SlidingWindow(1m) VelocityAggregate  ──▶ Redis Feature Store          │
│    ├── SlidingWindow(5m) VelocityAggregate  ──▶ Redis Feature Store          │
│    └── FraudScoringService (ONNX XGBoost)      float[22] → fraud_score      │
│         │                                                                     │
│         ├── score > 0.08 ──▶ txn-alerts topic + Tighten rate limit          │
│         └── score <= 0.08 ──▶ txn-scored topic ──▶ PostgreSQL                │
└─────────────────────────────────────────────────────────────────────────────┘
         │                          │
         ▼                          ▼
┌──────────────────┐   ┌──────────────────────────────────────────────────────┐
│ vanguard-alert   │   │ vanguard-limiter  :8083                               │
│ :8084            │   │                                                       │
│ WebSocket STOMP  │   │ TrafficMetrics ──▶ LSTM Forecaster ──▶ Adaptive     │
│ Live Dashboard   │   │ Collector        60-min ring buffer  Token Bucket    │
└──────────────────┘   └──────────────────────────────────────────────────────┘
```

### Data Flow

1. **Ingest**: HTTP POST goes through `@RateLimited` AOP aspect (token bucket), Redis Lua idempotency check, then published to Kafka `txn-raw`
2. **Scoring**: Kafka Streams consumes `txn-raw`, computes sliding window velocity features, extracts 22 features, runs ONNX inference
3. **Result**: If `fraud_score > 0.08`, transaction is marked `HIGH_RISK` and alert published; otherwise marked `SCORED` and persisted to PostgreSQL
4. **Limiter**: Traffic metrics aggregated per minute, LSTM predicts future load, token bucket capacities adjusted proactively

---

## ML Pipeline

### Fraud Model

| Component | Detail |
|---|---|
| **Training Data** | 1.5M transactions (0.13% fraud rate) |
| **Algorithm** | XGBoost classifier |
| **Feature Engineering** | 22 features: one-hot encoding, balance deltas, log transforms, cyclic time encoding, interaction features |
| **Sampling** | Balanced undersampling (10:1 legit-to-fraud) — SMOTE rejected due to sparse real fraud samples |
| **Validation** | ROC-AUC: 0.9785, AUPRC lift: 69.3x |
| **Runtime** | ONNX Runtime embedded in JVM — P99 < 5ms |

### LSTM Traffic Forecaster

| Component | Detail |
|---|---|
| **Training Data** | 50K synthetic samples (35 days @ 1-min intervals) |
| **Architecture** | LSTM (hidden=64, layers=2, dropout=0.2, lookback=60) |
| **Output** | Predicted requests-per-minute for next interval |
| **Adaptation** | predicted > 500 → tighten limits by 60%; predicted < 100 → relax |

---

## Quick Start

### Prerequisites

- Java 21 (Temurin)
- Maven 3.9+
- Docker Desktop

### Setup

```bash
# Build
git clone https://github.com/meetmehta136/VANGUARD.git
cd VANGUARD && mvn clean compile

# Start infrastructure
docker compose up -d

# Start services (4 terminals)
cd vanguard-ingest && mvn spring-boot:run    # :8081
cd vanguard-scoring && mvn spring-boot:run   # :8082
cd vanguard-limiter && mvn spring-boot:run   # :8083
cd vanguard-alert && mvn spring-boot:run     # :8084
```

### Test a Transaction

```bash
curl -X POST http://localhost:8081/api/v1/transactions \
  -H "Content-Type: application/json" \
  -d '{"transactionId":"txn-001","userId":"user-001","amount":5000.00,
       "merchantId":"merchant-001","transactionType":"CASH_OUT",
       "oldBalanceOrig":5000.00,"newBalanceOrig":0.00,
       "oldBalanceDest":0.00,"newBalanceDest":0.00,
       "latitude":21.17,"longitude":72.83,
       "deviceId":"dev-001","ipAddress":"1.2.3.4","currency":"INR"}'

# Check result
docker exec vanguard-postgres psql -U vanguard -d vanguard \
  -c "SELECT transaction_id, fraud_score, is_high_risk, status
      FROM transactions WHERE transaction_id='txn-001';"
```

---

## API Reference

### POST /api/v1/transactions

**Request Body:**

| Field | Type | Required | Description |
|---|---|---|---|
| `transactionId` | string | ✅ | Unique ID (idempotency key) |
| `userId` | string | ✅ | User identifier (Kafka partition key) |
| `amount` | number | ✅ | Transaction amount |
| `transactionType` | string | ✅ | `CASH_OUT`, `TRANSFER`, `PAYMENT`, `CASH_IN`, `DEBIT` |
| `oldBalanceOrig` | number | ✅ | Sender balance before |
| `newBalanceOrig` | number | ✅ | Sender balance after |
| `oldBalanceDest` | number | ✅ | Receiver balance before |
| `newBalanceDest` | number | ✅ | Receiver balance after |
| `latitude` | number | ✅ | Transaction latitude |
| `longitude` | number | ✅ | Transaction longitude |
| `deviceId` | string | ✅ | Device identifier |
| `ipAddress` | string | ✅ | Client IP |
| `currency` | string | ✅ | Currency code |

**Responses:**

| Status | Meaning |
|---|---|
| `202 Accepted` | Transaction queued for scoring |
| `200 OK` | Duplicate — already processed |
| `429 Too Many Requests` | Rate limit exceeded |
| `400 Bad Request` | Validation failure |

### Monitoring Endpoints

| URL | Purpose |
|---|---|
| `http://localhost:8082/actuator/health` | Scoring service health |
| `http://localhost:8082/actuator/prometheus` | Prometheus metrics |
| `http://localhost:8084/dashboard` | Live fraud alert dashboard |
| `http://localhost:8090` | Kafka UI |

---

## Performance

### k6 Load Test (3-minute sustained)

| Metric | Result | Threshold |
|---|---|---|
| p99 latency | **28.46ms** | < 500ms ✅ |
| Throughput | 56.5 req/s | — |
| Failures | **0** | < 15% ✅ |

### ONNX Inference

| Metric | Value |
|---|---|
| Cold start | ~800ms (one-time) |
| P50 inference | ~1ms |
| P99 inference | **< 5ms** |

---

## Project Structure

```
VANGUARD/
├── ml/                           ← Python ML training
│   ├── fraud/                    ← XGBoost feature engineering + ONNX export
│   └── traffic/                  ← LSTM training + ONNX export
├── vanguard-common/              ← Shared models, constants, Kafka topics
├── vanguard-ingest/              ← REST API, Redis idempotency, Kafka producer
├── vanguard-scoring/             ← ONNX inference, Kafka Streams topology
├── vanguard-limiter/             ← AOP rate limiting, LSTM forecaster
├── vanguard-alert/               ← WebSocket STOMP, live dashboard
├── load-test/                    ← k6 load test scripts
├── monitoring/                   ← Prometheus + Grafana config
├── docker-compose.yml            ← Infrastructure orchestration
└── pom.xml                       ← Maven multi-module root
```

## Services

| Service | Port | Responsibility |
|---|---|---|
| vanguard-ingest | 8081 | REST API, validation, Redis idempotency, Kafka producer |
| vanguard-scoring | 8082 | ONNX inference, Kafka Streams, Redis feature store |
| vanguard-limiter | 8083 | AOP rate limiting, LSTM forecaster |
| vanguard-alert | 8084 | WebSocket STOMP, live dashboard |

### Infrastructure

| Service | Port |
|---|---|
| PostgreSQL | 5432 |
| Redis | 6379 |
| Kafka | 9092 |
| Kafka UI | 8090 |
| Prometheus | 9090 |
| Grafana | 3000 |

### Kafka Topics

| Topic | Partitions | Key | Purpose |
|---|---|---|---|
| `txn-raw` | 3 | userId | Raw transactions from ingest |
| `txn-scored` | 3 | userId | Scored transactions |
| `txn-alerts` | 1 | userId | HIGH_RISK alerts only |
| `traffic-metrics` | 1 | global | Per-minute request counts |

---

## Configuration

### Rate Limiter Defaults

```yaml
ratelimit:
  default-capacity: 5          # tokens per user
  default-refill: 5            # tokens/minute
  high-risk-capacity: 1        # after HIGH_RISK detection
  global-tighten-factor: 0.6   # during predicted traffic spike
```

### Model Constants

```java
FRAUD_FEATURE_COUNT       = 22
FRAUD_HIGH_RISK_THRESHOLD = 0.08f
LSTM_LOOKBACK             = 60
LSTM_MEAN                 = 111.2f
LSTM_STD                  = 86.4f
```

---

## License

MIT

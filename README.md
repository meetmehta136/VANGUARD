# VANGUARD — Real-Time Fraud Intelligence Platform

Production-grade, real-time transaction fraud scoring using Java 21, Spring Boot 3.3.6, Kafka Streams, ONNX Runtime, and Redis.

**Work in Progress.** Building 16-task execution plan — ML feature engineering through deployable microservices.

## Architecture

```
Client → Ingest(8081) → Kafka → Streams (velocity features) → Redis
  → Scoring(8082) → ONNX XGBoost → Kafka → Alert Gateway(8084) → WebSocket
  → Traffic Metrics → LSTM Forecaster → Rate Limiter(8083)
```

## Modules

| Module | Port | Responsibility |
|--------|------|----------------|
| vanguard-common | — | Shared DTOs, constants, serdes |
| vanguard-ingest | 8081 | REST API, Kafka producer, idempotency |
| vanguard-scoring | 8082 | Kafka Streams, ONNX inference, feature store |
| vanguard-limiter | 8083 | Adaptive rate limiting, LSTM forecasting |
| vanguard-alert | 8084 | WebSocket fraud alert dashboard |

## Tech Stack

Java 21 · Spring Boot 3.3.6 · Apache Kafka 3.7 · Redis 7 · PostgreSQL 16
ONNX Runtime Java 1.17.0 · XGBoost · PyTorch LSTM · Testcontainers · Resilience4j

## Quick Start

```bash
# ML setup
ml\venv\Scripts\pip install -r ml\requirements.txt
```

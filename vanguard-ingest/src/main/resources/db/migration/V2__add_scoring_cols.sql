ALTER TABLE transactions
  ADD COLUMN IF NOT EXISTS fraud_score FLOAT,
  ADD COLUMN IF NOT EXISTS is_high_risk BOOLEAN DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'PENDING',
  ADD COLUMN IF NOT EXISTS scoring_latency_ms BIGINT,
  ADD COLUMN IF NOT EXISTS scored_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_fraud_score
  ON transactions(fraud_score);
CREATE INDEX IF NOT EXISTS idx_is_high_risk
  ON transactions(is_high_risk);

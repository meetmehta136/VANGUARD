CREATE TABLE IF NOT EXISTS transactions (
    transaction_id     VARCHAR(64)     PRIMARY KEY,
    user_id            VARCHAR(64)     NOT NULL,
    amount             DECIMAL(18, 4)  NOT NULL,
    old_balance_orig   DECIMAL(18, 4),
    new_balance_orig   DECIMAL(18, 4),
    old_balance_dest   DECIMAL(18, 4),
    new_balance_dest   DECIMAL(18, 4),
    transaction_type   VARCHAR(16)     NOT NULL,
    merchant_id        VARCHAR(64),
    merchant_category  VARCHAR(32),
    latitude           DOUBLE PRECISION,
    longitude          DOUBLE PRECISION,
    device_id          VARCHAR(64),
    ip_address         VARCHAR(45),
    currency           VARCHAR(3)      DEFAULT 'USD',
    timestamp          TIMESTAMP       NOT NULL,
    ingested_at        TIMESTAMP       NOT NULL DEFAULT NOW(),
    status             VARCHAR(16)     NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX IF NOT EXISTS idx_transactions_user_id ON transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_transactions_status ON transactions(status);
CREATE INDEX IF NOT EXISTS idx_transactions_timestamp ON transactions(timestamp);
CREATE INDEX IF NOT EXISTS idx_transactions_type ON transactions(transaction_type);

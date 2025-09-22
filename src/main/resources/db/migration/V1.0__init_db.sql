CREATE SCHEMA IF NOT EXISTS catalog;

CREATE TABLE IF NOT EXISTS catalog.products
(
    uuid             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    gold_id          BIGINT       NOT NULL UNIQUE,
    long_name        VARCHAR(255) NOT NULL,
    short_name       VARCHAR(155) NOT NULL,
    iow_unit_type    VARCHAR(100) NOT NULL,
    healthy_category VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_products_gold_id ON catalog.products (gold_id);

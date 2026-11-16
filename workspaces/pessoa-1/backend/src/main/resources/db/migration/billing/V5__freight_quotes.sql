CREATE TABLE billing.freight_quotes (
 id UUID PRIMARY KEY, order_id UUID NOT NULL UNIQUE,
 distance_km NUMERIC(12,3) NOT NULL CHECK (distance_km >= 0),
 tariff_per_km NUMERIC(12,2) NOT NULL CHECK (tariff_per_km >= 0),
 amount NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
 calculated_at TIMESTAMP WITH TIME ZONE NOT NULL, source VARCHAR(20) NOT NULL
);
CREATE INDEX freight_quotes_order_idx ON billing.freight_quotes(order_id, calculated_at);

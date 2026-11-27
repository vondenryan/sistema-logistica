CREATE TABLE stores (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX ux_stores_name_ci ON stores (LOWER(name));

CREATE INDEX ix_stores_created_at_id ON stores (created_at, id);

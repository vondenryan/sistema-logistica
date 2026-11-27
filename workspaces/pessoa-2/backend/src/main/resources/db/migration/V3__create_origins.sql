CREATE TABLE origins (
    id UUID PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    store_id UUID NOT NULL REFERENCES stores (id),
    address_line1 VARCHAR(200) NOT NULL,
    address_line2 VARCHAR(100),
    address_city VARCHAR(100) NOT NULL,
    address_state VARCHAR(2) NOT NULL,
    address_postal_code VARCHAR(9),
    address_country VARCHAR(2) NOT NULL,
    address_latitude DOUBLE PRECISION NOT NULL,
    address_longitude DOUBLE PRECISION NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_origins_type CHECK (type IN ('STORE', 'DISTRIBUTION_CENTER'))
);

CREATE INDEX ix_origins_created_at_id ON origins (created_at, id);
CREATE INDEX ix_origins_store_id ON origins (store_id);

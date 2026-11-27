CREATE TABLE suppliers (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    contact_email VARCHAR(254),
    store_id UUID NOT NULL REFERENCES stores (id),
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_suppliers_created_at_id ON suppliers (created_at, id);
CREATE INDEX ix_suppliers_store_id ON suppliers (store_id);

CREATE SCHEMA IF NOT EXISTS deliveries;

CREATE TABLE IF NOT EXISTS deliveries.deliveries (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE,
    route_id UUID,
    status VARCHAR(30) NOT NULL,
    receiver_name VARCHAR(150),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_deliveries_order_id ON deliveries.deliveries (order_id);
CREATE INDEX IF NOT EXISTS idx_deliveries_route_id ON deliveries.deliveries (route_id);
CREATE INDEX IF NOT EXISTS idx_deliveries_status ON deliveries.deliveries (status);

CREATE TABLE IF NOT EXISTS deliveries.delivery_events (
    id UUID PRIMARY KEY,
    delivery_id UUID NOT NULL REFERENCES deliveries.deliveries (id) ON DELETE RESTRICT,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    actor_id UUID NOT NULL,
    actor_role VARCHAR(30) NOT NULL,
    reason VARCHAR(500),
    receiver_name VARCHAR(150),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_delivery_events_delivery_id ON deliveries.delivery_events (delivery_id);
CREATE INDEX IF NOT EXISTS idx_delivery_events_occurred_at ON deliveries.delivery_events (occurred_at);

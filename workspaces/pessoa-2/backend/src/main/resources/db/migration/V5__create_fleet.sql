CREATE TABLE carriers (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX ux_carriers_name_ci ON carriers (LOWER(name));
CREATE INDEX ix_carriers_created_at_id ON carriers (created_at, id);

CREATE TABLE drivers (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    carrier_id UUID REFERENCES carriers (id),
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_drivers_created_at_id ON drivers (created_at, id);
CREATE INDEX ix_drivers_carrier_id ON drivers (carrier_id);

CREATE TABLE vehicles (
    id UUID PRIMARY KEY,
    identifier VARCHAR(40) NOT NULL,
    capacity_kg DECIMAL(12, 3) NOT NULL CHECK (capacity_kg > 0),
    fleet_type VARCHAR(16) NOT NULL CHECK (fleet_type IN ('OWN', 'THIRD_PARTY')),
    carrier_id UUID REFERENCES carriers (id),
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_vehicles_carrier_by_fleet_type CHECK (
        (fleet_type = 'OWN' AND carrier_id IS NULL)
        OR (fleet_type = 'THIRD_PARTY' AND carrier_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX ux_vehicles_identifier ON vehicles (identifier);
CREATE INDEX ix_vehicles_created_at_id ON vehicles (created_at, id);
CREATE INDEX ix_vehicles_carrier_id ON vehicles (carrier_id);

CREATE TABLE fleet_assignments (
    assignment_id UUID PRIMARY KEY,
    route_id UUID NOT NULL,
    origin_id UUID NOT NULL,
    required_capacity_kg DECIMAL(12, 3) NOT NULL CHECK (required_capacity_kg > 0),
    driver_id UUID NOT NULL REFERENCES drivers (id),
    vehicle_id UUID NOT NULL REFERENCES vehicles (id),
    carrier_id UUID REFERENCES carriers (id),
    fleet_type VARCHAR(16) NOT NULL CHECK (fleet_type IN ('OWN', 'THIRD_PARTY')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMPTZ,
    CONSTRAINT ux_fleet_assignments_route_id UNIQUE (route_id),
    CONSTRAINT ck_fleet_assignments_carrier_by_type CHECK (
        (fleet_type = 'OWN' AND carrier_id IS NULL)
        OR (fleet_type = 'THIRD_PARTY' AND carrier_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX ux_fleet_assignments_active_driver
    ON fleet_assignments (driver_id)
    WHERE released_at IS NULL;
CREATE UNIQUE INDEX ux_fleet_assignments_active_vehicle
    ON fleet_assignments (vehicle_id)
    WHERE released_at IS NULL;
CREATE INDEX ix_fleet_assignments_driver_id ON fleet_assignments (driver_id);
CREATE INDEX ix_fleet_assignments_vehicle_id ON fleet_assignments (vehicle_id);

CREATE TABLE fleet_release_events (
    event_id UUID PRIMARY KEY,
    route_id UUID NOT NULL,
    reason VARCHAR(32) NOT NULL CHECK (reason IN ('ROUTE_COMPLETED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

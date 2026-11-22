CREATE TABLE IF NOT EXISTS workspace_seed (
    seed_key VARCHAR(64) PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO workspace_seed (seed_key)
VALUES ('pessoa2-bootstrap-v1')
ON CONFLICT (seed_key) DO NOTHING;

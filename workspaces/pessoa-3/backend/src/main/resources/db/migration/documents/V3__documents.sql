CREATE SCHEMA IF NOT EXISTS documents;

CREATE TABLE IF NOT EXISTS documents.simulated_documents (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    document_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    pdf_content BYTEA NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_simulated_documents_order_type UNIQUE (order_id, document_type)
);

CREATE INDEX IF NOT EXISTS idx_simulated_documents_order_id
    ON documents.simulated_documents (order_id);

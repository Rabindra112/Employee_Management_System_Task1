ALTER TABLE documents
    MODIFY COLUMN employee_id BIGINT NULL,
    ADD COLUMN owner_user_id BIGINT NULL,
    ADD COLUMN latest_approved_version INT NULL,
    ADD CONSTRAINT fk_documents_owner_user FOREIGN KEY (owner_user_id) REFERENCES app_users(id);

ALTER TABLE document_versions
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN approved_by VARCHAR(255) NULL,
    ADD COLUMN approved_at DATETIME(6) NULL,
    ADD COLUMN rejected_by VARCHAR(255) NULL,
    ADD COLUMN rejected_at DATETIME(6) NULL,
    ADD COLUMN rejection_reason VARCHAR(1000) NULL;

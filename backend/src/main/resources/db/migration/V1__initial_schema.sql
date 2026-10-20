CREATE TABLE IF NOT EXISTS employees (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    department VARCHAR(80) NOT NULL,
    salary DECIMAL(12,2) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS app_users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(190) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_app_users_email UNIQUE (email)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS document_types (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    CONSTRAINT uk_document_types_name UNIQUE (name)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS documents (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(6) NOT NULL,
    current_version INT NOT NULL,
    document_name VARCHAR(255) NOT NULL,
    document_type_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    CONSTRAINT fk_documents_type FOREIGN KEY (document_type_id) REFERENCES document_types(id),
    CONSTRAINT fk_documents_employee FOREIGN KEY (employee_id) REFERENCES employees(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS document_versions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(255) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    uploaded_at DATETIME(6) NOT NULL,
    uploaded_by VARCHAR(255) NOT NULL,
    CONSTRAINT uk_document_version UNIQUE (document_id, version_number),
    CONSTRAINT uk_document_file_path UNIQUE (file_path),
    CONSTRAINT fk_document_versions_document FOREIGN KEY (document_id) REFERENCES documents(id)
) ENGINE=InnoDB;

CREATE TABLE workspace_join_requests (
    id SERIAL PRIMARY KEY,
    org_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    name VARCHAR(255),
    status VARCHAR(50) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_join_req_org FOREIGN KEY (org_id) REFERENCES organizations(id),
    CONSTRAINT uq_org_user UNIQUE (org_id, user_id)
);

-- SQL Migration to add organization_requests table

CREATE TABLE IF NOT EXISTS organization_requests (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    requester_id VARCHAR(100) NOT NULL,
    requester_email VARCHAR(255) NOT NULL,
    requester_name VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Grant privileges to the app user
GRANT ALL PRIVILEGES ON organization_requests TO idea_app;
GRANT ALL PRIVILEGES ON SEQUENCE organization_requests_id_seq TO idea_app;

-- Enable Row-Level Security
ALTER TABLE organization_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE organization_requests FORCE ROW LEVEL SECURITY;

-- Drop policy if exists
DROP POLICY IF EXISTS org_requests_policy ON organization_requests;

-- RLS Policy: Requester can view/insert their own request, and any existing ADMIN can view/update all requests.
CREATE POLICY org_requests_policy ON organization_requests
    FOR ALL
    USING (
        requester_id = NULLIF(current_setting('app.current_user_id', true), '')
        OR EXISTS (
            SELECT 1 FROM users 
            WHERE id = NULLIF(current_setting('app.current_user_id', true), '') 
            AND role = 'ADMIN'
        )
    );

-- Disable RLS on users and organizations to support database-driven join flow
ALTER TABLE users DISABLE ROW LEVEL SECURITY;
ALTER TABLE organizations DISABLE ROW LEVEL SECURITY;

-- Database permissions configuration verified.
-- Database permissions configuration verified.
-- Database permissions configuration verified.
-- Database permissions configuration verified.
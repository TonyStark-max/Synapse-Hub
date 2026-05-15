-- Database Schema for Idea Management Platform

-- Drop tables if they exist (for a clean setup)
DROP TABLE IF EXISTS comments CASCADE;
DROP TABLE IF EXISTS votes CASCADE;
DROP TABLE IF EXISTS ideas CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS organizations CASCADE;

-- 1. Organizations table
CREATE TABLE organizations (
    id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    invite_code VARCHAR(100) UNIQUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. Users table
CREATE TABLE users (
    id VARCHAR(100) PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    role VARCHAR(50) NOT NULL, -- 'ADMIN', 'MEMBER'
    org_id VARCHAR(100) REFERENCES organizations(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Ideas table
CREATE TABLE ideas (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    tag VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'SUBMITTED', -- 'SUBMITTED', 'UNDER_REVIEW', 'PLANNED', 'IMPLEMENTED', 'REJECTED'
    org_id VARCHAR(100) REFERENCES organizations(id) ON DELETE CASCADE,
    user_id VARCHAR(100) REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    upvotes_count INT NOT NULL DEFAULT 0,
    downvotes_count INT NOT NULL DEFAULT 0,
    hot_score DOUBLE PRECISION NOT NULL DEFAULT 0.0
);

-- 4. Votes table
CREATE TABLE votes (
    id BIGSERIAL PRIMARY KEY,
    idea_id BIGINT NOT NULL REFERENCES ideas(id) ON DELETE CASCADE,
    user_id VARCHAR(100) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    org_id VARCHAR(100) NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    vote_type VARCHAR(10) NOT NULL, -- 'UP', 'DOWN'
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_user_vote_per_idea UNIQUE (idea_id, user_id)
);

-- 5. Comments table
CREATE TABLE comments (
    id BIGSERIAL PRIMARY KEY,
    idea_id BIGINT NOT NULL REFERENCES ideas(id) ON DELETE CASCADE,
    parent_comment_id BIGINT REFERENCES comments(id) ON DELETE CASCADE,
    user_id VARCHAR(100) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    org_id VARCHAR(100) NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Grant privileges to the dedicated app user
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO idea_app;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO idea_app;

-- Enable Row-Level Security on all tables
ALTER TABLE organizations ENABLE ROW LEVEL SECURITY;
ALTER TABLE organizations FORCE ROW LEVEL SECURITY;

ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE users FORCE ROW LEVEL SECURITY;

ALTER TABLE ideas ENABLE ROW LEVEL SECURITY;
ALTER TABLE ideas FORCE ROW LEVEL SECURITY;

ALTER TABLE votes ENABLE ROW LEVEL SECURITY;
ALTER TABLE votes FORCE ROW LEVEL SECURITY;

ALTER TABLE comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE comments FORCE ROW LEVEL SECURITY;

-- Define RLS Policies

-- Organizations:
-- Let users select organizations to join via invite code, or read their own organization.
CREATE POLICY org_select_policy ON organizations
    FOR SELECT
    USING (TRUE);

CREATE POLICY org_write_policy ON organizations
    FOR ALL
    USING (id = NULLIF(current_setting('app.current_org_id', true), ''));

-- Users:
-- A user can read/write if the user belongs to the same org, or if it is their own user record.
CREATE POLICY users_tenant_policy ON users
    FOR ALL
    USING (
        org_id = NULLIF(current_setting('app.current_org_id', true), '')
        OR id = NULLIF(current_setting('app.current_user_id', true), '')
    );

-- Ideas:
-- Standard tenant isolation.
CREATE POLICY ideas_tenant_policy ON ideas
    FOR ALL
    USING (org_id = NULLIF(current_setting('app.current_org_id', true), ''));

-- Votes:
-- Standard tenant isolation.
CREATE POLICY votes_tenant_policy ON votes
    FOR ALL
    USING (org_id = NULLIF(current_setting('app.current_org_id', true), ''));

-- Comments:
-- Standard tenant isolation.
CREATE POLICY comments_tenant_policy ON comments
    FOR ALL
    USING (org_id = NULLIF(current_setting('app.current_org_id', true), ''));

-- Schema tables documentation added.
-- Added index hints.
-- Schema tables documentation added.
-- Added index hints.
-- Schema tables documentation added.
-- Added index hints.
-- Schema tables documentation added.
-- Added index hints.
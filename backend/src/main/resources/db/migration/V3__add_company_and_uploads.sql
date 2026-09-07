-- Create companies table
CREATE TABLE companies (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert some default companies
INSERT INTO companies (id, name) VALUES ('google', 'Google');
INSERT INTO companies (id, name) VALUES ('microsoft', 'Microsoft');
INSERT INTO companies (id, name) VALUES ('amazon', 'Amazon');
INSERT INTO companies (id, name) VALUES ('meta', 'Meta');
INSERT INTO companies (id, name) VALUES ('apple', 'Apple');
INSERT INTO companies (id, name) VALUES ('netflix', 'Netflix');

-- Update users table
ALTER TABLE users ADD COLUMN company_id VARCHAR(255);
ALTER TABLE users ADD COLUMN profile_pic_url TEXT;

-- Update organizations table
ALTER TABLE organizations ADD COLUMN company_id VARCHAR(255);

-- Update organization_requests table
ALTER TABLE organization_requests ADD COLUMN company_id VARCHAR(255);

-- Update ideas table
ALTER TABLE ideas ADD COLUMN image_url TEXT;

-- Update comments table
ALTER TABLE comments ADD COLUMN image_url TEXT;

-- Add foreign keys (optional, but good for integrity)
ALTER TABLE users ADD CONSTRAINT fk_user_company FOREIGN KEY (company_id) REFERENCES companies(id);
ALTER TABLE organizations ADD CONSTRAINT fk_org_company FOREIGN KEY (company_id) REFERENCES companies(id);
ALTER TABLE organization_requests ADD CONSTRAINT fk_org_req_company FOREIGN KEY (company_id) REFERENCES companies(id);

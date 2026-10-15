CREATE TABLE users (
	id BIGSERIAL PRIMARY KEY,
	username VARCHAR(30) NOT NULL,
	email VARCHAR(254) NOT NULL,
	email_verified_at TIMESTAMPTZ,
	-- Null for accounts created with Google that never set a password.
	password_hash VARCHAR(100),
	google_id VARCHAR(100),
	role VARCHAR(10) NOT NULL DEFAULT 'USER',
	display_name VARCHAR(50) NOT NULL,
	avatar_url VARCHAR(500),
	-- Editor.js JSON.
	profile_content JSONB,
	status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
	last_seen_at TIMESTAMPTZ,
	created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
	deleted_at TIMESTAMPTZ,
	CONSTRAINT users_role_check CHECK (role IN ('USER', 'ADMIN')),
	CONSTRAINT users_status_check CHECK (status IN ('ACTIVE', 'BANNED'))
);

-- Usernames and emails are unique regardless of case (FR-1.2).
CREATE UNIQUE INDEX users_username_lower_ux ON users (lower(username));
CREATE UNIQUE INDEX users_email_lower_ux ON users (lower(email));
CREATE UNIQUE INDEX users_google_id_ux ON users (google_id) WHERE google_id IS NOT NULL;

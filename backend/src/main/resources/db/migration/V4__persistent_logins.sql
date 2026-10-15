-- "Remember me" logins (Spring Security's PersistentTokenRepository). One row per remembered
-- device: the series stays fixed and the token value changes each time the cookie is used.
-- The username (which can't change) is the same key the session table uses.
CREATE TABLE persistent_logins (
	series VARCHAR(64) PRIMARY KEY,
	username VARCHAR(64) NOT NULL,
	token VARCHAR(64) NOT NULL,
	-- The token before the last rotation, accepted for a few seconds so that tabs a browser
	-- restores in parallel (all sending the same cookie) don't look like a stolen cookie.
	previous_token VARCHAR(64),
	rotated_at TIMESTAMPTZ,
	last_used TIMESTAMPTZ NOT NULL
);

CREATE INDEX persistent_logins_username_ix ON persistent_logins (username);

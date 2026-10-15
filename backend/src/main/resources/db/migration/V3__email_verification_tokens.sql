-- Only a hash of each token is stored, so a database leak doesn't hand out working links.
CREATE TABLE email_verification_tokens (
	id BIGSERIAL PRIMARY KEY,
	user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
	-- The address the link verifies; it differs from users.email while an address change is pending.
	email VARCHAR(254) NOT NULL,
	token_hash CHAR(64) NOT NULL,
	expires_at TIMESTAMPTZ NOT NULL,
	used_at TIMESTAMPTZ,
	created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX email_verification_tokens_hash_ux ON email_verification_tokens (token_hash);
CREATE INDEX email_verification_tokens_user_ix ON email_verification_tokens (user_id);

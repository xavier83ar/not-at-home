-- Like email_verification_tokens: only a hash of each token is stored.
CREATE TABLE password_reset_tokens (
	id BIGSERIAL PRIMARY KEY,
	user_id BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
	token_hash CHAR(64) NOT NULL,
	expires_at TIMESTAMPTZ NOT NULL,
	used_at TIMESTAMPTZ,
	created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX password_reset_tokens_hash_ux ON password_reset_tokens (token_hash);
CREATE INDEX password_reset_tokens_user_ix ON password_reset_tokens (user_id);

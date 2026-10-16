CREATE TABLE uploads (
	id BIGSERIAL PRIMARY KEY,
	owner_id BIGINT NOT NULL REFERENCES users (id),
	storage_key VARCHAR(100) NOT NULL,
	content_type VARCHAR(20) NOT NULL,
	size_bytes INTEGER NOT NULL,
	created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The storage key is the random file name; looking up by it must never hit two rows.
CREATE UNIQUE INDEX uploads_storage_key_ux ON uploads (storage_key);

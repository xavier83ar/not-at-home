CREATE TABLE categories (
	id BIGSERIAL PRIMARY KEY,
	name VARCHAR(100) NOT NULL,
	slug VARCHAR(100) NOT NULL,
	description VARCHAR(500) NOT NULL,
	cover_upload_id BIGINT REFERENCES uploads (id),
	position INTEGER NOT NULL,
	created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
	deleted_at TIMESTAMPTZ
);

-- The slug is what public URLs look categories up by (FR-2.1); fixed once set, so this only
-- guards against two categories ending up with the same one.
CREATE UNIQUE INDEX categories_slug_ux ON categories (slug) WHERE deleted_at IS NULL;

CREATE TABLE topics (
	id BIGSERIAL PRIMARY KEY,
	category_id BIGINT NOT NULL REFERENCES categories (id),
	author_id BIGINT NOT NULL REFERENCES users (id),
	-- FR-3.1: at most 128 characters.
	title VARCHAR(128) NOT NULL,
	-- Cosmetic part of the URL (/topics/123-the-slug); lookups use the id, so it may be stale.
	slug VARCHAR(100) NOT NULL,
	is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
	is_locked BOOLEAN NOT NULL DEFAULT FALSE,
	-- Time of the latest visible post; sorts topic lists (FR-3.3).
	last_post_at TIMESTAMPTZ NOT NULL,
	-- Visible replies, not counting the opening post.
	reply_count INTEGER NOT NULL DEFAULT 0,
	created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
	deleted_at TIMESTAMPTZ
);

-- Topic lists: one category, pinned first, then latest activity (FR-3.3).
CREATE INDEX topics_category_activity_ix
	ON topics (category_id, is_pinned DESC, last_post_at DESC)
	WHERE deleted_at IS NULL;
CREATE INDEX topics_author_ix ON topics (author_id);

CREATE TABLE posts (
	id BIGSERIAL PRIMARY KEY,
	topic_id BIGINT NOT NULL REFERENCES topics (id),
	author_id BIGINT NOT NULL REFERENCES users (id),
	-- Editor.js JSON, validated and sanitized by the backend before it is stored.
	content JSONB NOT NULL,
	-- Plain text taken from content; what full-text search (M6) reads.
	search_text TEXT NOT NULL,
	edited_at TIMESTAMPTZ,
	created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
	deleted_at TIMESTAMPTZ
);

-- Posts of a topic, oldest first (FR-4.3); ids grow with time, so they give the order.
CREATE INDEX posts_topic_ix ON posts (topic_id, id);
CREATE INDEX posts_author_ix ON posts (author_id);

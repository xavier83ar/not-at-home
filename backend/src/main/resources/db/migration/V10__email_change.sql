-- A token that changes the account's address when used (FR-8.3), instead of verifying the current
-- one. The address stays in `email`; users.email only changes once the link is used.
ALTER TABLE email_verification_tokens ADD COLUMN email_change BOOLEAN NOT NULL DEFAULT FALSE;

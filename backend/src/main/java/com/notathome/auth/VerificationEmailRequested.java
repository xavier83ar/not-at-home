package com.notathome.auth;

/**
 * Published inside the transaction that created the token; the email goes out after commit. {@code
 * addressChange} says the link changes the account's address (FR-8.3) instead of verifying it.
 */
record VerificationEmailRequested(
    Long userId, String email, String displayName, String token, boolean addressChange) {}

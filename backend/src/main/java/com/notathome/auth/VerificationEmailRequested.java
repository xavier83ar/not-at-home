package com.notathome.auth;

/** Published inside the transaction that created the token; the email goes out after commit. */
record VerificationEmailRequested(Long userId, String email, String displayName, String token) {}

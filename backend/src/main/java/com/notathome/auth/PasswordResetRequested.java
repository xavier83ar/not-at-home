package com.notathome.auth;

/**
 * Someone asked for a reset email for this address. Handled on another thread: the lookup, the
 * token and the email all happen there, so the request does the same (nothing) for every address.
 */
record PasswordResetRequested(String email) {}

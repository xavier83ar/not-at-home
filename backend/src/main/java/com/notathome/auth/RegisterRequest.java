package com.notathome.auth;

import com.notathome.common.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** FR-1.1 to FR-1.3. The username can't be changed later, the display name can. */
record RegisterRequest(
    @NotNull
        @Size(min = 3, max = 30, message = "must be 3 to 30 characters")
        @Pattern(regexp = "[A-Za-z0-9_-]*", message = "can only have letters, digits, _ and -")
        String username,
    @NotBlank @Size(max = 50, message = "must be at most 50 characters") String displayName,
    @NotBlank
        @Size(max = 254, message = "is too long")
        @Email(message = "must be a valid email address")
        @Pattern(regexp = ".+@.+\\..+", message = "must be a valid email address")
        String email,
    // bcrypt only reads the first 72 bytes, and refusing longer input beats silently cutting it.
    @NotNull
        @Size(min = 8, message = "must be at least 8 characters")
        @MaxUtf8Bytes(value = 72, message = "must be at most 72 bytes (about 72 characters)")
        String password) {}

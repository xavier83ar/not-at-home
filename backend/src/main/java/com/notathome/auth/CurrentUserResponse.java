package com.notathome.auth;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.notathome.users.User;
import com.notathome.users.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

/** The logged-in user as seen by the frontend. The email is only ever shown to its owner. */
public record CurrentUserResponse(
    @Schema(requiredMode = REQUIRED) Long id,
    @Schema(requiredMode = REQUIRED) String username,
    @Schema(requiredMode = REQUIRED) String displayName,
    @Schema(requiredMode = REQUIRED) String email,
    @Schema(requiredMode = REQUIRED) UserRole role,
    @Schema(requiredMode = REQUIRED) boolean emailVerified,
    @Schema(requiredMode = NOT_REQUIRED, nullable = true) String avatarUrl) {

  static CurrentUserResponse from(User user) {
    return new CurrentUserResponse(
        user.getId(),
        user.getUsername(),
        user.getDisplayName(),
        user.getEmail(),
        user.getRole(),
        user.isEmailVerified(),
        user.getAvatarUrl());
  }
}

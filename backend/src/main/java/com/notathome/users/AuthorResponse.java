package com.notathome.users;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Who wrote something, as shown next to it (FR-4.2). Never carries the email (NFR-8). A deleted
 * account shows as "Deleted user" (FR-8.4), and {@code deleted} tells the frontend not to link to a
 * profile page it doesn't have.
 */
public record AuthorResponse(
    @Schema(requiredMode = REQUIRED) Long id,
    @Schema(requiredMode = REQUIRED) String username,
    @Schema(requiredMode = REQUIRED) String displayName,
    @Schema(requiredMode = NOT_REQUIRED, nullable = true) String avatarUrl,
    @Schema(requiredMode = REQUIRED) UserRole role,
    @Schema(requiredMode = REQUIRED) boolean deleted) {

  public static AuthorResponse from(User user) {
    if (user.isDeleted()) {
      return new AuthorResponse(
          user.getId(), user.getUsername(), "Deleted user", null, UserRole.USER, true);
    }
    return new AuthorResponse(
        user.getId(),
        user.getUsername(),
        user.getDisplayName(),
        user.getAvatarUrl(),
        user.getRole(),
        false);
  }
}

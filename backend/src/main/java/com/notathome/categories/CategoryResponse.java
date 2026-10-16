package com.notathome.categories;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * FR-2.2. {@code topicCount}, {@code postCount} and {@code latestActivityAt} are always zero/null
 * until M4 adds topics; this response shape is ready for them.
 */
public record CategoryResponse(
    @Schema(requiredMode = REQUIRED) Long id,
    @Schema(requiredMode = REQUIRED) String name,
    @Schema(requiredMode = REQUIRED) String slug,
    @Schema(requiredMode = REQUIRED) String description,
    @Schema(requiredMode = NOT_REQUIRED, nullable = true) String coverImageUrl,
    @Schema(requiredMode = REQUIRED) int position,
    @Schema(requiredMode = REQUIRED) long topicCount,
    @Schema(requiredMode = REQUIRED) long postCount,
    @Schema(requiredMode = NOT_REQUIRED, nullable = true) Instant latestActivityAt) {

  /** {@code coverImageUrl} is resolved by the caller: a category only stores the upload's id. */
  static CategoryResponse from(Category category, String coverImageUrl) {
    return new CategoryResponse(
        category.getId(),
        category.getName(),
        category.getSlug(),
        category.getDescription(),
        coverImageUrl,
        category.getPosition(),
        0,
        0,
        null);
  }
}

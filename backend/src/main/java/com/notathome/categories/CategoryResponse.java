package com.notathome.categories;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * FR-2.2. The counts cover active topics and visible posts only; {@code latestActivityAt} is null
 * for a category without topics. The admin endpoints leave all three at zero/null: nothing there
 * needs them.
 */
public record CategoryResponse(
    @Schema(requiredMode = REQUIRED) Long id,
    @Schema(requiredMode = REQUIRED) String name,
    @Schema(requiredMode = REQUIRED) String slug,
    @Schema(requiredMode = REQUIRED) String description,
    @Schema(requiredMode = NOT_REQUIRED, nullable = true) String coverImageUrl,
    // So editing a category can resubmit the same cover without re-uploading it; not sensitive.
    @Schema(requiredMode = NOT_REQUIRED, nullable = true) Long coverUploadId,
    @Schema(requiredMode = REQUIRED) int position,
    @Schema(requiredMode = REQUIRED) long topicCount,
    @Schema(requiredMode = REQUIRED) long postCount,
    @Schema(requiredMode = NOT_REQUIRED, nullable = true) Instant latestActivityAt) {

  /** {@code coverImageUrl} is resolved by the caller: a category only stores the upload's id. */
  static CategoryResponse from(Category category, String coverImageUrl) {
    return from(category, coverImageUrl, 0, 0, null);
  }

  static CategoryResponse from(
      Category category,
      String coverImageUrl,
      long topicCount,
      long postCount,
      Instant latestActivityAt) {
    return new CategoryResponse(
        category.getId(),
        category.getName(),
        category.getSlug(),
        category.getDescription(),
        coverImageUrl,
        category.getCoverUploadId(),
        category.getPosition(),
        topicCount,
        postCount,
        latestActivityAt);
  }
}

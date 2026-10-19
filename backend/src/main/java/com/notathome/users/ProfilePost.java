package com.notathome.users;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.notathome.topics.CategoryRef;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** One of a user's recent posts on their profile (FR-8.1): where it is and how it starts. */
public record ProfilePost(
    @Schema(requiredMode = REQUIRED) Long postId,
    @Schema(requiredMode = REQUIRED) Long topicId,
    @Schema(requiredMode = REQUIRED) String topicSlug,
    @Schema(requiredMode = REQUIRED) String topicTitle,
    @Schema(requiredMode = REQUIRED) CategoryRef category,
    @Schema(requiredMode = REQUIRED, description = "Plain text, shortened") String excerpt,
    @Schema(requiredMode = REQUIRED) Instant createdAt) {}

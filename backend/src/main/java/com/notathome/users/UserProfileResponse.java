package com.notathome.users;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.notathome.content.EditorDocumentSchema;
import com.notathome.topics.TopicSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * A public profile (FR-8.1). Never carries the email (NFR-8). {@code postCount} and the recent
 * lists only count content that is still visible (FR-5.5).
 */
public record UserProfileResponse(
    @Schema(requiredMode = REQUIRED) Long id,
    @Schema(requiredMode = REQUIRED) String username,
    @Schema(requiredMode = REQUIRED) String displayName,
    @Schema(requiredMode = NOT_REQUIRED, nullable = true) String avatarUrl,
    @Schema(requiredMode = REQUIRED) UserRole role,
    @Schema(requiredMode = REQUIRED) Instant joinedAt,
    @Schema(requiredMode = REQUIRED) long postCount,
    @Schema(
            requiredMode = NOT_REQUIRED,
            nullable = true,
            implementation = EditorDocumentSchema.class)
        JsonNode profileContent,
    @Schema(requiredMode = REQUIRED) List<TopicSummary> recentTopics,
    @Schema(requiredMode = REQUIRED) List<ProfilePost> recentPosts) {}

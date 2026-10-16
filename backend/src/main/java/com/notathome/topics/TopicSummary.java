package com.notathome.topics;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.notathome.users.AuthorResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * A topic as listed (FR-3.2). {@code lastPostId} is for the link to its latest post (FR-7.2);
 * {@code category} is for the lists that mix categories (home, all topics).
 */
public record TopicSummary(
    @Schema(requiredMode = REQUIRED) Long id,
    @Schema(requiredMode = REQUIRED) String slug,
    @Schema(requiredMode = REQUIRED) String title,
    @Schema(requiredMode = REQUIRED) AuthorResponse author,
    @Schema(requiredMode = REQUIRED) CategoryRef category,
    @Schema(requiredMode = REQUIRED) boolean pinned,
    @Schema(requiredMode = REQUIRED) boolean locked,
    @Schema(requiredMode = REQUIRED) int replyCount,
    @Schema(requiredMode = REQUIRED) Instant createdAt,
    @Schema(requiredMode = REQUIRED) Instant lastPostAt,
    @Schema(requiredMode = REQUIRED) Long lastPostId) {}

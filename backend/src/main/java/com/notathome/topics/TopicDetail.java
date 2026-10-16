package com.notathome.topics;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.notathome.users.AuthorResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** The topic itself, above its posts. */
public record TopicDetail(
    @Schema(requiredMode = REQUIRED) Long id,
    @Schema(requiredMode = REQUIRED) String slug,
    @Schema(requiredMode = REQUIRED) String title,
    @Schema(requiredMode = REQUIRED) CategoryRef category,
    @Schema(requiredMode = REQUIRED) AuthorResponse author,
    @Schema(requiredMode = REQUIRED) boolean pinned,
    @Schema(requiredMode = REQUIRED) boolean locked,
    @Schema(requiredMode = REQUIRED) int replyCount,
    @Schema(requiredMode = REQUIRED) Instant createdAt,
    @Schema(requiredMode = REQUIRED) Instant lastPostAt) {}

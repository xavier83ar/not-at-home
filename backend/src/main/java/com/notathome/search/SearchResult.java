package com.notathome.search;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.notathome.topics.CategoryRef;
import com.notathome.users.AuthorResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * One matching post (FR-6.2). {@code snippet} is HTML with no tag other than {@code <mark>} around
 * the matched words, everything else escaped, so it can be shown as it is.
 */
public record SearchResult(
    @Schema(requiredMode = REQUIRED) Long postId,
    @Schema(requiredMode = REQUIRED) Long topicId,
    @Schema(requiredMode = REQUIRED) String topicSlug,
    @Schema(requiredMode = REQUIRED) String topicTitle,
    @Schema(requiredMode = REQUIRED) CategoryRef category,
    @Schema(requiredMode = REQUIRED) AuthorResponse author,
    @Schema(requiredMode = REQUIRED) String snippet,
    @Schema(requiredMode = REQUIRED) Instant createdAt) {}

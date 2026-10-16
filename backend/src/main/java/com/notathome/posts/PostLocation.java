package com.notathome.posts;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

/** Where a post lives, for its permanent link (FR-4.6): the topic and which page of it. */
public record PostLocation(
    @Schema(requiredMode = REQUIRED) Long postId,
    @Schema(requiredMode = REQUIRED) Long topicId,
    @Schema(requiredMode = REQUIRED) String topicSlug,
    @Schema(requiredMode = REQUIRED) int page) {}

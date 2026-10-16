package com.notathome.topics;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.notathome.common.PageResponse;
import com.notathome.posts.PostResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/** A topic and one page of its posts, oldest first (FR-4.3). */
public record TopicPageResponse(
    @Schema(requiredMode = REQUIRED) TopicDetail topic,
    @Schema(requiredMode = REQUIRED) PageResponse<PostResponse> posts) {}

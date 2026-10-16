package com.notathome.topics;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.notathome.common.PageResponse;
import com.notathome.posts.PostResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * A topic and one page of its posts, oldest first (FR-4.3).
 *
 * <p>For a removed topic (FR-5.7) {@code removed} is true and {@code recentTopics} lists the most
 * recently active topics of its category; {@code topic} and {@code posts} are then only present for
 * admins (FR-5.8).
 */
public record TopicPageResponse(
    @Schema(requiredMode = REQUIRED) boolean removed,
    @Schema(requiredMode = REQUIRED) CategoryRef category,
    @Schema(requiredMode = NOT_REQUIRED, nullable = true) TopicDetail topic,
    @Schema(requiredMode = NOT_REQUIRED, nullable = true) PageResponse<PostResponse> posts,
    @Schema(requiredMode = REQUIRED) List<TopicSummary> recentTopics) {}

package com.notathome.home;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.notathome.categories.CategoryResponse;
import com.notathome.topics.TopicSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** Everything the home page shows (section 8.2), in one request. */
public record HomeResponse(
    @Schema(requiredMode = REQUIRED) List<CategoryResponse> highlightedCategories,
    @Schema(requiredMode = REQUIRED) List<TopicSummary> latestTopics,
    @Schema(requiredMode = REQUIRED) List<ActiveUserResponse> activeUsers) {}

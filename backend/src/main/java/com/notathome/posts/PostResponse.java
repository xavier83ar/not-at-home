package com.notathome.posts;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.notathome.users.AuthorResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import tools.jackson.databind.JsonNode;

/**
 * A post as shown in a topic. A removed post (FR-5.6) has {@code removed: true} and, for everyone
 * but admins (FR-5.8), no {@code content}.
 */
public record PostResponse(
    @Schema(requiredMode = REQUIRED) Long id,
    @Schema(requiredMode = REQUIRED) Long topicId,
    @Schema(requiredMode = REQUIRED) AuthorResponse author,
    @Schema(
            requiredMode = NOT_REQUIRED,
            nullable = true,
            type = "object",
            description = "Editor.js document: {blocks: [{type, data}]}")
        JsonNode content,
    @Schema(requiredMode = REQUIRED) boolean removed,
    @Schema(requiredMode = REQUIRED, description = "Whether this is the topic's opening post")
        boolean opening,
    @Schema(requiredMode = REQUIRED) Instant createdAt,
    @Schema(requiredMode = NOT_REQUIRED, nullable = true) Instant editedAt) {}

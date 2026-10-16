package com.notathome.topics;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/** FR-3.1. {@code content} is the opening post, as an Editor.js document. */
record CreateTopicRequest(
    @NotNull Long categoryId,
    @NotBlank @Size(max = 128, message = "must be at most 128 characters") String title,
    @NotNull @Schema(type = "object", description = "Editor.js document: {blocks: [{type, data}]}")
        JsonNode content) {}

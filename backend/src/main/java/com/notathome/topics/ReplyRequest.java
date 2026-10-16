package com.notathome.topics;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

/** FR-4.1. */
record ReplyRequest(
    @NotNull @Schema(type = "object", description = "Editor.js document: {blocks: [{type, data}]}")
        JsonNode content) {}

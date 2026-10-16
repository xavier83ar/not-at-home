package com.notathome.topics;

import com.notathome.content.EditorDocumentSchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

/** FR-4.1. */
record ReplyRequest(
    @NotNull @Schema(implementation = EditorDocumentSchema.class) JsonNode content) {}

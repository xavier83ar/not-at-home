package com.notathome.posts;

import com.notathome.content.EditorDocumentSchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

/** The body of a reply (FR-4.1) or of an edit (FR-5.1): the post's new content. */
public record PostContentRequest(
    @NotNull @Schema(implementation = EditorDocumentSchema.class) JsonNode content) {}

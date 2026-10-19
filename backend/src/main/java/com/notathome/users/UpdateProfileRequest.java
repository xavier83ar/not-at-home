package com.notathome.users;

import com.notathome.content.EditorDocumentSchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

/**
 * FR-8.2. {@code profileContent} is optional: leaving it out, or sending a document with nothing in
 * it, clears the profile text.
 */
record UpdateProfileRequest(
    @NotBlank @Size(max = 50, message = "must be at most 50 characters") String displayName,
    @Schema(
            requiredMode = Schema.RequiredMode.NOT_REQUIRED,
            nullable = true,
            implementation = EditorDocumentSchema.class)
        JsonNode profileContent) {}

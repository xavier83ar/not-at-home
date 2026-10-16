package com.notathome.content;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/**
 * Only describes an Editor.js document in the OpenAPI spec, so the frontend's generated types are
 * more useful than "any JSON". The API itself reads and writes the document as a plain JSON tree
 * ({@link EditorContentProcessor} checks it), not through this type.
 */
@Schema(name = "EditorDocument")
public record EditorDocumentSchema(@Schema(requiredMode = REQUIRED) List<Block> blocks) {

  @Schema(name = "EditorBlock")
  public record Block(
      @Schema(requiredMode = REQUIRED) String type,
      @Schema(requiredMode = REQUIRED) Map<String, Object> data) {}
}

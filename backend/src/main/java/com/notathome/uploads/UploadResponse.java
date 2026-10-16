package com.notathome.uploads;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

public record UploadResponse(
    @Schema(requiredMode = REQUIRED) Long id,
    @Schema(requiredMode = REQUIRED) String url,
    @Schema(requiredMode = REQUIRED) String contentType,
    @Schema(requiredMode = REQUIRED) long size) {

  static UploadResponse from(Upload upload) {
    return new UploadResponse(
        upload.getId(), UploadUrl.of(upload), upload.getContentType(), upload.getSizeBytes());
  }
}

package com.notathome.uploads;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** {@code app.uploads.dir}: the local-disk directory files are stored under. */
@Validated
@ConfigurationProperties("app.uploads")
public record UploadProperties(@NotBlank String dir) {}

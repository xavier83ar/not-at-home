package com.notathome.topics;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

/** Enough of a category to link to it (breadcrumbs, NFR-1, and the topics listed outside it). */
public record CategoryRef(
    @Schema(requiredMode = REQUIRED) Long id,
    @Schema(requiredMode = REQUIRED) String name,
    @Schema(requiredMode = REQUIRED) String slug) {}

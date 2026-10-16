package com.notathome.categories;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** FR-2.1. {@code coverUploadId} is optional: a category can exist before its cover is set. */
record CategoryRequest(
    @NotBlank @Size(max = 100, message = "must be at most 100 characters") String name,
    @NotBlank @Size(max = 500, message = "must be at most 500 characters") String description,
    Long coverUploadId) {}

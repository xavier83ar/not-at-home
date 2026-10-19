package com.notathome.users;

import jakarta.validation.constraints.NotNull;

/** FR-8.2: the id of an image the user uploaded through {@code POST /api/uploads}. */
record AvatarRequest(@NotNull Long uploadId) {}

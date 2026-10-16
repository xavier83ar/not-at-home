package com.notathome.categories;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Every active category's id, in the desired order (arrow-button reordering, not drag-and-drop).
 */
record ReorderCategoriesRequest(@NotEmpty List<Long> orderedIds) {}

package com.notathome.search;

import java.util.Optional;

/** FR-6.4: best match first (the default) or newest first. */
enum SearchSort {
  RELEVANCE,
  DATE;

  /** Reads the {@code sort} parameter ({@code relevance} or {@code date}); empty if unknown. */
  static Optional<SearchSort> parse(String value) {
    for (SearchSort sort : values()) {
      if (sort.name().equalsIgnoreCase(value)) {
        return Optional.of(sort);
      }
    }
    return Optional.empty();
  }
}

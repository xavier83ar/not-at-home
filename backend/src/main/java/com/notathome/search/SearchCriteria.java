package com.notathome.search;

import java.time.Instant;

/**
 * A validated search (FR-6.1, FR-6.3). Every filter is optional: {@code null} means "any".
 *
 * @param keywords what to look for, as typed
 * @param categorySlug only posts in this category
 * @param author only posts by this username, whatever its case
 * @param from only posts written at or after this moment
 * @param until only posts written before this moment (so the last day of a range is whole)
 */
record SearchCriteria(
    String keywords,
    String categorySlug,
    String author,
    Instant from,
    Instant until,
    SearchSort sort) {}

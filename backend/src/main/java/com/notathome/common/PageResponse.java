package com.notathome.common;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * One page of a paginated list (FR-7.1): the items, the page number (1-based, as in the URLs), and
 * how many pages and items there are in all.
 */
public record PageResponse<T>(
    @Schema(requiredMode = REQUIRED) List<T> items,
    @Schema(requiredMode = REQUIRED) int page,
    @Schema(requiredMode = REQUIRED) int totalPages,
    @Schema(requiredMode = REQUIRED) long totalItems) {

  /** Every list in the forum shows this many items per page (FR-7.1). */
  public static final int PAGE_SIZE = 10;

  /** The request for page {@code page} (1-based); anything below 1 means the first page. */
  public static PageRequest request(int page, Sort sort) {
    return PageRequest.of(Math.max(page, 1) - 1, PAGE_SIZE, sort);
  }

  /**
   * Turns a page of entities into a response. {@code mapper} gets the whole page at once, so it can
   * look related rows (authors...) up in one query instead of one per item.
   *
   * @throws ResponseStatusException 404 for a page past the end, which would only ever be empty
   */
  public static <E, T> PageResponse<T> of(Page<E> page, Function<List<E>, List<T>> mapper) {
    if (page.getNumber() > 0 && page.getNumber() >= page.getTotalPages()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    return new PageResponse<>(
        mapper.apply(page.getContent()),
        page.getNumber() + 1,
        Math.max(page.getTotalPages(), 1),
        page.getTotalElements());
  }
}

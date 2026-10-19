package com.notathome.search;

import com.notathome.common.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SearchController {

  private final SearchService search;

  SearchController(SearchService search) {
    this.search = search;
  }

  /**
   * FR-6: public. {@code q} is required (3 to 100 characters). {@code category} is a slug, {@code
   * author} a username, {@code from} and {@code to} are days ({@code 2026-01-31}, UTC, both
   * included), and {@code sort} is {@code relevance} (the default) or {@code date}. {@code page} is
   * 1-based (FR-7.1).
   */
  @GetMapping("/api/search")
  PageResponse<SearchResult> search(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String author,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) String sort,
      @RequestParam(defaultValue = "1") int page) {
    return search.search(q, category, author, from, to, sort, page);
  }
}

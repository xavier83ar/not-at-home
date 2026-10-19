package com.notathome.search;

import com.notathome.common.FieldErrorsException;
import com.notathome.common.PageResponse;
import com.notathome.topics.CategoryRef;
import com.notathome.users.AuthorResponse;
import com.notathome.users.Authors;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

@Service
class SearchService {

  static final int MIN_KEYWORDS = 3;
  static final int MAX_KEYWORDS = 100;

  private final SearchRepository search;
  private final Authors authors;

  SearchService(SearchRepository search, Authors authors) {
    this.search = search;
    this.authors = authors;
  }

  /**
   * FR-6.1 to FR-6.5. Blank parameters count as not given, since a search form sends every field.
   * An unknown category or author is not an error: nothing matches, so the page is empty.
   */
  @Transactional(readOnly = true)
  PageResponse<SearchResult> search(
      String keywords,
      String category,
      String author,
      String from,
      String to,
      String sort,
      int page) {
    SearchCriteria criteria = validate(keywords, category, author, from, to, sort);
    return PageResponse.of(
        search.search(criteria, PageResponse.request(page, Sort.unsorted())), this::results);
  }

  private static SearchCriteria validate(
      String keywords, String category, String author, String from, String to, String sort) {
    Map<String, List<String>> errors = new HashMap<>();

    String text = blankToNull(keywords);
    if (text == null || text.length() < MIN_KEYWORDS || text.length() > MAX_KEYWORDS) {
      errors.put(
          "q",
          List.of("must be between %d and %d characters".formatted(MIN_KEYWORDS, MAX_KEYWORDS)));
    }

    LocalDate fromDate = date("from", from, errors);
    LocalDate toDate = date("to", to, errors);
    if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
      errors.put("to", List.of("must not be before the start date"));
    }

    String sortText = blankToNull(sort);
    SearchSort order = SearchSort.RELEVANCE;
    if (sortText != null) {
      order = SearchSort.parse(sortText).orElse(null);
      if (order == null) {
        errors.put("sort", List.of("must be relevance or date"));
      }
    }

    if (!errors.isEmpty()) {
      throw new FieldErrorsException(errors);
    }
    return new SearchCriteria(
        text,
        blankToNull(category),
        blankToNull(author),
        fromDate == null ? null : fromDate.atStartOfDay(ZoneOffset.UTC).toInstant(),
        // The whole last day is included, so the range ends where the next day starts.
        toDate == null ? null : toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
        order);
  }

  private static LocalDate date(String field, String value, Map<String, List<String>> errors) {
    String text = blankToNull(value);
    if (text == null) {
      return null;
    }
    try {
      return LocalDate.parse(text);
    } catch (DateTimeParseException e) {
      errors.put(field, List.of("must be a date like 2026-01-31"));
      return null;
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private List<SearchResult> results(List<SearchRepository.Hit> hits) {
    Map<Long, AuthorResponse> byAuthor =
        authors.byId(hits.stream().map(SearchRepository.Hit::authorId).distinct().toList());
    return hits.stream()
        .map(
            hit ->
                new SearchResult(
                    hit.postId(),
                    hit.topicId(),
                    hit.topicSlug(),
                    hit.topicTitle(),
                    new CategoryRef(hit.categoryId(), hit.categoryName(), hit.categorySlug()),
                    byAuthor.get(hit.authorId()),
                    snippet(hit.snippet()),
                    hit.createdAt()))
        .toList();
  }

  /**
   * The post's text is plain text, so it is escaped as a whole, and only then are the match markers
   * turned into {@code <mark>}. Escaping after {@code ts_headline} (never before) keeps the text
   * from being cut in the middle of an entity, and nothing a user wrote can become a tag.
   */
  static String snippet(String headline) {
    return HtmlUtils.htmlEscape(headline)
        .replace(String.valueOf(SearchRepository.MATCH_START), "<mark>")
        .replace(String.valueOf(SearchRepository.MATCH_END), "</mark>");
  }
}

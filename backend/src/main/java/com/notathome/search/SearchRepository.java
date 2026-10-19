package com.notathome.search;

import com.notathome.common.PageResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Full-text search over posts and topic titles (FR-6.1) with PostgreSQL. It is plain SQL rather
 * than a JPA query because the filters are optional: a condition is only added for a filter that
 * was given, which a fixed query can't do without PostgreSQL failing to type its null parameters.
 */
@Repository
class SearchRepository {

  /** A matching post and the topic and category it is in, before the author is looked up. */
  record Hit(
      Long postId,
      Long topicId,
      String topicSlug,
      String topicTitle,
      Long categoryId,
      String categoryName,
      String categorySlug,
      Long authorId,
      String snippet,
      Instant createdAt) {}

  /**
   * Marks the matched words in a snippet. Control characters can't be in a post's text (they are
   * stripped before the headline is made), so the service can escape the whole snippet as HTML and
   * only then turn these into {@code <mark>}.
   */
  static final char MATCH_START = '\u0001';

  static final char MATCH_END = '\u0002';

  /**
   * A short text is shown whole; a long one is cut to a stretch of 15 to 35 words around the match.
   */
  private static final String HEADLINE_OPTIONS =
      "StartSel="
          + MATCH_START
          + ", StopSel="
          + MATCH_END
          + ", MaxFragments=0, MinWords=15,"
          + " MaxWords=35";

  private static final String QUERY =
      "WITH q AS (SELECT websearch_to_tsquery('english', :keywords) AS query)";

  /**
   * A post matches by its text, or by its topic's title; the title match belongs to the topic's
   * first post that is still visible. That is the opening post unless it was removed (FR-5.3), and
   * it never shows a removed post (FR-6.5).
   */
  private static final String FIRST_VISIBLE_POST =
      "p.id = (SELECT min(f.id) FROM posts f WHERE f.topic_id = t.id AND f.deleted_at IS NULL)";

  private static final String FROM =
      " FROM posts p JOIN topics t ON t.id = p.topic_id"
          + " JOIN categories c ON c.id = t.category_id"
          + " JOIN users u ON u.id = p.author_id CROSS JOIN q";

  /** FR-6.5: deleted topics, posts and accounts never appear. Posts of banned users do. */
  private static final String ALWAYS =
      " p.deleted_at IS NULL AND t.deleted_at IS NULL AND c.deleted_at IS NULL"
          + " AND u.deleted_at IS NULL"
          + " AND (p.search_vector @@ q.query OR (t.title_vector @@ q.query AND "
          + FIRST_VISIBLE_POST
          + "))";

  /** A title match counts double, but only on the post it belongs to. */
  private static final String SCORE =
      "ts_rank(p.search_vector, q.query) + CASE WHEN t.title_vector @@ q.query AND "
          + FIRST_VISIBLE_POST
          + " THEN 2 * ts_rank(t.title_vector, q.query) ELSE 0 END";

  private final NamedParameterJdbcTemplate jdbc;

  SearchRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  Page<Hit> search(SearchCriteria criteria, Pageable pageable) {
    MapSqlParameterSource params = new MapSqlParameterSource("keywords", criteria.keywords());
    String where = ALWAYS + filters(criteria, params);

    Long total =
        jdbc.queryForObject(
            QUERY + " SELECT count(*)" + FROM + " WHERE" + where, params, Long.class);
    if (total == null || total == 0) {
      return Page.empty(pageable);
    }

    params
        .addValue("limit", PageResponse.PAGE_SIZE)
        .addValue("offset", pageable.getOffset())
        .addValue("headlineOptions", HEADLINE_OPTIONS);
    boolean byDate = criteria.sort() == SearchSort.DATE;
    // Ties are broken by id, or rows would move between pages as the list is paged through.
    String pageOrder = byDate ? "p.created_at DESC, p.id DESC" : "score DESC, p.id DESC";
    String finalOrder =
        byDate ? "h.created_at DESC, h.post_id DESC" : "h.score DESC, h.post_id DESC";
    // The page is picked first, so the (costly) snippets are only made for its rows.
    String sql =
        QUERY
            + ", h AS (SELECT p.id AS post_id, p.created_at, "
            + SCORE
            + " AS score"
            + FROM
            + " WHERE"
            + where
            + " ORDER BY "
            + pageOrder
            + " LIMIT :limit OFFSET :offset)"
            + " SELECT p.id, p.topic_id, t.slug AS topic_slug, t.title AS topic_title,"
            + " c.id AS category_id, c.name AS category_name, c.slug AS category_slug,"
            + " p.author_id, p.created_at,"
            + " ts_headline('english', translate(p.search_text, chr(1) || chr(2), ''), q.query,"
            + " :headlineOptions) AS snippet"
            + " FROM h JOIN posts p ON p.id = h.post_id JOIN topics t ON t.id = p.topic_id"
            + " JOIN categories c ON c.id = t.category_id CROSS JOIN q ORDER BY "
            + finalOrder;

    List<Hit> hits =
        jdbc.query(
            sql,
            params,
            (rs, row) ->
                new Hit(
                    rs.getLong("id"),
                    rs.getLong("topic_id"),
                    rs.getString("topic_slug"),
                    rs.getString("topic_title"),
                    rs.getLong("category_id"),
                    rs.getString("category_name"),
                    rs.getString("category_slug"),
                    rs.getLong("author_id"),
                    rs.getString("snippet"),
                    rs.getObject("created_at", OffsetDateTime.class).toInstant()));
    return new PageImpl<>(hits, pageable, total);
  }

  /** The conditions for the filters that were given. */
  private static String filters(SearchCriteria criteria, MapSqlParameterSource params) {
    List<String> conditions = new ArrayList<>();
    if (criteria.categorySlug() != null) {
      conditions.add("c.slug = :category");
      params.addValue("category", criteria.categorySlug());
    }
    if (criteria.author() != null) {
      conditions.add("lower(u.username) = lower(:author)");
      params.addValue("author", criteria.author());
    }
    if (criteria.from() != null) {
      conditions.add("p.created_at >= :from");
      params.addValue("from", OffsetDateTime.ofInstant(criteria.from(), ZoneOffset.UTC));
    }
    if (criteria.until() != null) {
      conditions.add("p.created_at < :until");
      params.addValue("until", OffsetDateTime.ofInstant(criteria.until(), ZoneOffset.UTC));
    }
    return conditions.stream().map(condition -> " AND " + condition).reduce("", String::concat);
  }
}

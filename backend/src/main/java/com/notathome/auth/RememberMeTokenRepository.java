package com.notathome.auth;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

/**
 * Remember-me tokens in the {@code persistent_logins} table. Like Spring's JDBC repository, plus
 * what is needed to accept parallel requests from one browser (see {@link AuthRememberMeServices}):
 * the token is replaced with a compare-and-set, and the one before it is kept for a few seconds.
 */
class RememberMeTokenRepository implements PersistentTokenRepository {

  /** How long the token before the latest one is still accepted. */
  static final int GRACE_SECONDS = 10;

  /**
   * One remembered device. {@code previousTokenInGrace} is decided by the database clock, so it
   * doesn't depend on the application servers agreeing on the time.
   */
  record Stored(
      String username,
      String series,
      String token,
      String previousToken,
      boolean previousTokenInGrace,
      Instant lastUsed) {}

  private final JdbcTemplate jdbc;

  RememberMeTokenRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void createNewToken(PersistentRememberMeToken token) {
    jdbc.update(
        "insert into persistent_logins (series, username, token, last_used) values (?, ?, ?, ?)",
        token.getSeries(),
        token.getUsername(),
        token.getTokenValue(),
        new Timestamp(token.getDate().getTime()));
  }

  @Override
  public void updateToken(String series, String tokenValue, java.util.Date lastUsed) {
    jdbc.update(
        "update persistent_logins set token = ?, last_used = ? where series = ?",
        tokenValue,
        new Timestamp(lastUsed.getTime()),
        series);
  }

  @Override
  public PersistentRememberMeToken getTokenForSeries(String series) {
    return find(series)
        .map(
            stored ->
                new PersistentRememberMeToken(
                    stored.username(),
                    stored.series(),
                    stored.token(),
                    java.util.Date.from(stored.lastUsed())))
        .orElse(null);
  }

  @Override
  public void removeUserTokens(String username) {
    jdbc.update("delete from persistent_logins where username = ?", username);
  }

  Optional<Stored> find(String series) {
    return jdbc
        .query(
            "select username, series, token, previous_token, last_used,"
                + " coalesce(rotated_at > now() - make_interval(secs => ?), false) as in_grace"
                + " from persistent_logins where series = ?",
            (rs, row) ->
                new Stored(
                    rs.getString("username"),
                    rs.getString("series"),
                    rs.getString("token"),
                    rs.getString("previous_token"),
                    rs.getBoolean("in_grace"),
                    rs.getTimestamp("last_used").toInstant()),
            GRACE_SECONDS,
            series)
        .stream()
        .findFirst();
  }

  /**
   * Replaces {@code presentedToken} with {@code newToken} and remembers the old one. Returns false
   * if the stored token is no longer {@code presentedToken}, i.e. another request got there first.
   */
  boolean rotate(String series, String presentedToken, String newToken) {
    return jdbc.update(
            "update persistent_logins set previous_token = token, token = ?, rotated_at = now(),"
                + " last_used = now() where series = ? and token = ?",
            newToken,
            series,
            presentedToken)
        == 1;
  }
}

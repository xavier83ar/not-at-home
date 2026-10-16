package com.notathome.support;

import static com.notathome.support.Accounts.PASSWORD;
import static com.notathome.support.Accounts.login;
import static com.notathome.support.Accounts.session;

import com.notathome.users.User;
import com.notathome.users.UserRepository;
import com.notathome.users.UserRole;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** Helpers for tests of topics and posts. */
public final class Forum {

  private Forum() {}

  /**
   * Empties every table the forum writes to. Topics and posts reference users, categories and
   * uploads, so tests that create them call this before and after, which also leaves the database
   * clean for the tests that delete users and categories one table at a time.
   */
  public static void reset(JdbcTemplate jdbc) {
    jdbc.execute(
        "truncate table posts, topics, categories, uploads, users, spring_session,"
            + " persistent_logins cascade");
  }

  /** Creates the account directly in the database and logs in as it. */
  public static Cookie userSession(
      MockMvc mockMvc,
      UserRepository users,
      PasswordEncoder passwordEncoder,
      String username,
      UserRole role,
      boolean verified)
      throws Exception {
    User user =
        new User(username, username + "@example.com", username, passwordEncoder.encode(PASSWORD));
    user.setRole(role);
    if (verified) {
      user.markEmailVerified(Instant.now());
    }
    users.save(user);
    return session(login(mockMvc, username, PASSWORD, false));
  }

  /** An Editor.js document with one paragraph per argument. */
  public static String document(String... paragraphs) {
    return "{\"blocks\":["
        + Arrays.stream(paragraphs)
            .map(text -> "{\"type\":\"paragraph\",\"data\":{\"text\":\"" + text + "\"}}")
            .collect(Collectors.joining(","))
        + "]}";
  }
}

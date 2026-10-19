package com.notathome.support;

import static com.notathome.support.Accounts.PASSWORD;
import static com.notathome.support.Accounts.login;
import static com.notathome.support.Accounts.session;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.notathome.users.User;
import com.notathome.users.UserRepository;
import com.notathome.users.UserRole;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

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

  /** A request with a JSON body, sent the way the browser does, with the CSRF token. */
  public static ResultActions send(
      MockMvc mockMvc, HttpMethod method, Cookie who, String url, String json) throws Exception {
    return mockMvc.perform(
        request(method, url)
            .cookie(who)
            .with(Csrf.token(mockMvc))
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  /** Creates a topic and returns its id. */
  public static Long topic(MockMvc mockMvc, Cookie who, Long categoryId, String title)
      throws Exception {
    return topic(mockMvc, who, categoryId, title, "Opening post");
  }

  /** Creates a topic whose opening post says {@code text}, and returns its id. */
  public static Long topic(MockMvc mockMvc, Cookie who, Long categoryId, String title, String text)
      throws Exception {
    String body =
        send(
                mockMvc,
                HttpMethod.POST,
                who,
                "/api/topics",
                "{\"categoryId\":%d,\"title\":\"%s\",\"content\":%s}"
                    .formatted(categoryId, title, document(text)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return ((Number) JsonPath.read(body, "$.id")).longValue();
  }

  /** Replies to a topic and returns the new post's id. */
  public static Long reply(MockMvc mockMvc, Cookie who, Long topicId, String text)
      throws Exception {
    String body =
        send(
                mockMvc,
                HttpMethod.POST,
                who,
                "/api/topics/" + topicId + "/posts",
                "{\"content\":" + document(text) + "}")
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return ((Number) JsonPath.read(body, "$.id")).longValue();
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

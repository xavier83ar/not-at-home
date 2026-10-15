package com.notathome.ratelimit;

import static com.notathome.support.Accounts.PASSWORD;
import static com.notathome.support.Accounts.register;
import static com.notathome.support.Accounts.session;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.notathome.support.AsyncTasks;
import com.notathome.support.Csrf;
import com.notathome.support.ManualClock;
import com.notathome.support.RateLimitedIntegrationTest;
import com.notathome.support.RecordingMailSender;
import com.notathome.users.User;
import com.notathome.users.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@RateLimitedIntegrationTest
class RateLimitTests {

  static final String IP = "203.0.113.10";

  @Autowired MockMvc mockMvc;
  @Autowired ManualClock clock;
  @Autowired RateLimiter limiter;
  @Autowired RecordingMailSender mail;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired JdbcTemplate jdbc;

  @Autowired
  @Qualifier("applicationTaskExecutor")
  ThreadPoolTaskExecutor taskExecutor;

  @BeforeEach
  void cleanUp() {
    clock.advance(Duration.ofDays(2)); // every bucket from an earlier test is full again
    jdbc.update("delete from persistent_logins");
    jdbc.update("delete from spring_session");
    jdbc.update("delete from users");
    mail.reset();
  }

  /** A JSON POST from the given client address, with a valid CSRF token. */
  MvcResult send(String path, String ip, String json) throws Exception {
    return mockMvc.perform(request(path, ip, json).with(Csrf.token(mockMvc))).andReturn();
  }

  MockHttpServletRequestBuilder request(String path, String ip, String json) {
    return post(path)
        .contentType(MediaType.APPLICATION_JSON)
        .content(json)
        .with(
            r -> {
              r.setRemoteAddr(ip);
              return r;
            });
  }

  int status(MvcResult result) {
    return result.getResponse().getStatus();
  }

  static String loginJson(String login, String password) {
    return "{\"login\":\"%s\",\"password\":\"%s\"}".formatted(login, password);
  }

  static String forgotJson(String email) {
    return "{\"email\":\"%s\"}".formatted(email);
  }

  User createUser(String username) {
    return users.save(
        new User(username, username + "@example.com", username, passwordEncoder.encode(PASSWORD)));
  }

  @Test
  void tenLoginAttemptsAMinuteThenTooManyRequests() throws Exception {
    for (int i = 0; i < 10; i++) {
      assertThat(status(send("/api/auth/login", IP, loginJson("nobody", "wrong-password"))))
          .isEqualTo(401);
    }

    MvcResult refused = send("/api/auth/login", IP, loginJson("nobody", "wrong-password"));

    assertThat(status(refused)).isEqualTo(429);
    assertThat(refused.getResponse().getHeader("Retry-After")).isEqualTo("6");
    assertThat(refused.getResponse().getContentType()).contains("application/problem+json");
    assertThat(refused.getResponse().getContentAsString())
        .contains("\"code\":\"RATE_LIMITED\"", "\"retryAfterSeconds\":6", "\"status\":429");
  }

  @Test
  void aTokenComesBackAfterTheRetryAfterTime() throws Exception {
    for (int i = 0; i < 10; i++) {
      send("/api/auth/login", IP, loginJson("nobody", "wrong-password"));
    }
    assertThat(status(send("/api/auth/login", IP, loginJson("nobody", "x")))).isEqualTo(429);

    clock.advance(Duration.ofSeconds(6));

    assertThat(status(send("/api/auth/login", IP, loginJson("nobody", "x")))).isEqualTo(401);
    assertThat(status(send("/api/auth/login", IP, loginJson("nobody", "x")))).isEqualTo(429);
  }

  @Test
  void eachClientHasItsOwnLimit() throws Exception {
    for (int i = 0; i < 10; i++) {
      send("/api/auth/login", IP, loginJson("nobody", "wrong-password"));
    }

    assertThat(status(send("/api/auth/login", IP, loginJson("nobody", "x")))).isEqualTo(429);
    assertThat(status(send("/api/auth/login", "198.51.100.77", loginJson("nobody", "x"))))
        .isEqualTo(401);
  }

  @Test
  void successfulLoginsCountToo() throws Exception {
    createUser("alice");
    for (int i = 0; i < 10; i++) {
      assertThat(status(send("/api/auth/login", IP, loginJson("alice", PASSWORD)))).isEqualTo(200);
    }

    assertThat(status(send("/api/auth/login", IP, loginJson("alice", PASSWORD)))).isEqualTo(429);
  }

  @Test
  void malformedRequestsCountToo() throws Exception {
    for (int i = 0; i < 10; i++) {
      assertThat(status(send("/api/auth/login", IP, "{not json"))).isEqualTo(400);
    }

    assertThat(status(send("/api/auth/login", IP, "{not json"))).isEqualTo(429);
  }

  @Test
  void requestsRejectedByCsrfProtectionDoNotUseTheLimit() throws Exception {
    for (int i = 0; i < 30; i++) {
      MvcResult result =
          mockMvc.perform(request("/api/auth/login", IP, loginJson("nobody", "x"))).andReturn();
      assertThat(status(result)).isEqualTo(403);
    }

    assertThat(status(send("/api/auth/login", IP, loginJson("nobody", "x")))).isEqualTo(401);
  }

  @Test
  void fiveRegistrationsAnHourPerClient() throws Exception {
    for (int i = 0; i < 5; i++) {
      String json =
          "{\"username\":\"user%d\",\"displayName\":\"U\",\"email\":\"u%d@example.com\",\"password\":\"%s\"}"
              .formatted(i, i, PASSWORD);
      assertThat(status(send("/api/auth/register", IP, json))).isEqualTo(201);
    }

    MvcResult refused = send("/api/auth/register", IP, "{}");

    assertThat(status(refused)).isEqualTo(429);
    assertThat(refused.getResponse().getHeader("Retry-After"))
        .isEqualTo("720"); // one per 12 minutes
    clock.advance(Duration.ofMinutes(12));
    assertThat(status(send("/api/auth/register", IP, "{}")))
        .as("back to validation")
        .isEqualTo(400);
  }

  @Test
  void fiveResetRequestsAnHourPerClient() throws Exception {
    for (int i = 0; i < 5; i++) {
      assertThat(
              status(
                  send(
                      "/api/auth/forgot-password", IP, forgotJson("a%d@example.com".formatted(i)))))
          .isEqualTo(204);
    }

    assertThat(status(send("/api/auth/forgot-password", IP, forgotJson("a9@example.com"))))
        .isEqualTo(429);
  }

  @Test
  void tenResetAttemptsAndThirtyVerificationAttemptsAnHourPerClient() throws Exception {
    for (int i = 0; i < 10; i++) {
      assertThat(
              status(
                  send(
                      "/api/auth/reset-password",
                      IP,
                      "{\"token\":\"nope\",\"password\":\"password1\"}")))
          .isEqualTo(400);
    }
    assertThat(
            status(
                send(
                    "/api/auth/reset-password",
                    IP,
                    "{\"token\":\"nope\",\"password\":\"password1\"}")))
        .isEqualTo(429);

    for (int i = 0; i < 30; i++) {
      assertThat(status(send("/api/auth/verify-email", IP, "{\"token\":\"nope\"}"))).isEqualTo(400);
    }
    assertThat(status(send("/api/auth/verify-email", IP, "{\"token\":\"nope\"}"))).isEqualTo(429);
  }

  @Test
  void theLimitsOfDifferentEndpointsAreSeparate() throws Exception {
    for (int i = 0; i < 10; i++) {
      send("/api/auth/login", IP, loginJson("nobody", "x"));
    }

    assertThat(status(send("/api/auth/login", IP, loginJson("nobody", "x")))).isEqualTo(429);
    assertThat(status(send("/api/auth/forgot-password", IP, forgotJson("a@example.com"))))
        .isEqualTo(204);
  }

  @Test
  void readingIsNeverLimited() throws Exception {
    for (int i = 0; i < 200; i++) {
      assertThat(mockMvc.perform(get("/api/me")).andReturn().getResponse().getStatus())
          .isEqualTo(401);
      assertThat(mockMvc.perform(get("/api/csrf")).andReturn().getResponse().getStatus())
          .isEqualTo(204);
    }
  }

  @Test
  void aMailboxGetsAtMostThreeResetEmailsAnHourWhoeverAsks() throws Exception {
    createUser("alice");
    // Different clients each time, so only the per-address limit can be what stops it.
    for (int i = 0; i < 6; i++) {
      MvcResult result =
          send(
              "/api/auth/forgot-password",
              "198.51.100." + (10 + i),
              forgotJson("ALICE@example.com"));
      assertThat(status(result)).as("request %d is answered the same way", i).isEqualTo(204);
      assertThat(result.getResponse().getContentAsString()).isEmpty();
    }
    AsyncTasks.awaitIdle(taskExecutor);

    assertThat(mail.sent()).as("three emails, the rest dropped").hasSize(3);

    clock.advance(Duration.ofMinutes(20)); // one token every 20 minutes
    send("/api/auth/forgot-password", "198.51.100.99", forgotJson("alice@example.com"));
    AsyncTasks.awaitIdle(taskExecutor);
    assertThat(mail.sent()).hasSize(4);
  }

  @Test
  void unknownAddressesUseUpTheSameBudgetSoNothingGivesThemAway() throws Exception {
    for (int i = 0; i < 3; i++) {
      send("/api/auth/forgot-password", "198.51.100." + (10 + i), forgotJson("nobody@example.com"));
    }
    AsyncTasks.awaitIdle(taskExecutor);

    assertThat(limiter.tryConsume(RateLimitPolicy.PASSWORD_RESET_ADDRESS, "nobody@example.com"))
        .isFalse();
    assertThat(mail.sent()).isEmpty();
  }

  @Test
  void aMailboxGetsAtMostFiveVerificationEmailsAnHourRegistrationIncluded() throws Exception {
    Cookie session = session(register(mockMvc, "alice", "alice@example.com", PASSWORD));
    await().atMost(Duration.ofSeconds(5)).until(() -> mail.sent().size() == 1);

    for (int i = 0; i < 4; i++) { // 1 from registration + 4 resends = the 5 allowed
      MvcResult resend = resend(session, "198.51.100." + (20 + i));
      assertThat(status(resend)).isEqualTo(204);
    }
    MvcResult refused = resend(session, "198.51.100.30");

    assertThat(mail.sent()).hasSize(5);
    assertThat(status(refused)).isEqualTo(429);
    assertThat(refused.getResponse().getContentAsString())
        .contains("RATE_LIMITED", "retryAfterSeconds");
    assertThat(Integer.parseInt(refused.getResponse().getHeader("Retry-After"))).isBetween(1, 720);
  }

  @Test
  void registeringWithAnAddressThatIsOutOfBudgetStillWorksButSendsNothing() throws Exception {
    for (int i = 0; i < 5; i++) {
      limiter.consume(RateLimitPolicy.VERIFICATION_EMAIL_ADDRESS, "victim@example.com");
    }

    MvcResult registered = register(mockMvc, "mallory", "victim@example.com", PASSWORD);

    assertThat(status(registered)).isEqualTo(201);
    assertThat(mail.sent()).isEmpty();
    assertThat(status(resend(session(registered), "198.51.100.40")))
        .as("and the user is told")
        .isEqualTo(429);
  }

  @Test
  void resendIsAlsoLimitedPerClient() throws Exception {
    Cookie session = session(register(mockMvc, "alice", "alice@example.com", PASSWORD));
    for (int i = 0; i < 10; i++) {
      limiter.consume(RateLimitPolicy.RESEND_VERIFICATION, IP);
    }

    assertThat(status(resend(session, IP))).isEqualTo(429);
  }

  private MvcResult resend(Cookie session, String ip) throws Exception {
    return mockMvc
        .perform(
            post("/api/auth/resend-verification")
                .cookie(session)
                .with(
                    r -> {
                      r.setRemoteAddr(ip);
                      return r;
                    })
                .with(Csrf.token(mockMvc)))
        .andReturn();
  }
}

package com.notathome.auth;

import static com.notathome.support.Accounts.PASSWORD;
import static com.notathome.support.Accounts.lastCookie;
import static com.notathome.support.Accounts.login;
import static com.notathome.support.Accounts.session;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.notathome.support.AsyncTasks;
import com.notathome.support.Csrf;
import com.notathome.support.IntegrationTest;
import com.notathome.support.RecordingMailSender;
import com.notathome.users.User;
import com.notathome.users.UserRepository;
import com.notathome.users.UserStatus;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

@IntegrationTest
class PasswordResetTests {

  static final String NEW_PASSWORD = "a brand new password";

  @Autowired MockMvc mockMvc;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired JdbcTemplate jdbc;
  @Autowired RecordingMailSender mail;

  @Autowired
  @Qualifier("applicationTaskExecutor")
  ThreadPoolTaskExecutor taskExecutor;

  @BeforeEach
  void cleanUp() {
    jdbc.update("delete from persistent_logins");
    jdbc.update("delete from spring_session");
    jdbc.update("delete from users");
    mail.reset();
  }

  User createUser(String username) {
    return users.save(
        new User(username, username + "@example.com", username, passwordEncoder.encode(PASSWORD)));
  }

  MvcResult forgot(String email) throws Exception {
    return mockMvc
        .perform(
            post("/api/auth/forgot-password")
                .with(Csrf.token(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(email)))
        .andReturn();
  }

  MvcResult reset(String token, String password, Cookie... cookies) throws Exception {
    MockHttpServletRequestBuilder request =
        post("/api/auth/reset-password")
            .with(Csrf.token(mockMvc))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"%s\",\"password\":\"%s\"}".formatted(token, password));
    if (cookies.length > 0) {
      request.cookie(cookies);
    }
    return mockMvc.perform(request).andReturn();
  }

  /** Asks for a reset and returns the token from the email that arrives. */
  String requestToken(String email) throws Exception {
    int before = mail.sent().size();
    assertThat(forgot(email).getResponse().getStatus()).isEqualTo(204);
    await().atMost(Duration.ofSeconds(10)).until(() -> mail.sent().size() > before);
    return mail.last().resetToken();
  }

  int loginStatus(String login, String password) throws Exception {
    return login(mockMvc, login, password, false).getResponse().getStatus();
  }

  int meStatus(Cookie... cookies) throws Exception {
    return mockMvc.perform(get("/api/me").cookie(cookies)).andReturn().getResponse().getStatus();
  }

  @Test
  void aKnownAddressGetsAResetEmail() throws Exception {
    createUser("alice");

    String token = requestToken("alice@example.com");

    RecordingMailSender.Sent email = mail.last();
    assertThat(email.to()).isEqualTo("alice@example.com");
    assertThat(email.subject()).isEqualTo("Reset your password");
    assertThat(email.text())
        .contains("Hi alice", "http://localhost:3000/reset-password?token=", "1 hour");
    assertThat(email.html()).contains("cid:logo", "Choose a new password");
    assertThat(token).hasSize(43);
    String stored =
        jdbc.queryForObject("select token_hash from password_reset_tokens", String.class);
    assertThat(stored).hasSize(64).isEqualTo(SecureTokens.hash(token)).isNotEqualTo(token);
  }

  @Test
  void theAddressIsMatchedIgnoringCase() throws Exception {
    createUser("alice");

    requestToken("ALICE@Example.COM");

    assertThat(mail.last().to()).isEqualTo("alice@example.com");
  }

  @Test
  void anUnknownAddressGetsExactlyTheSameAnswerAndNoEmail() throws Exception {
    createUser("alice");

    MvcResult known = forgot("alice@example.com");
    MvcResult unknown = forgot("nobody@example.com");
    AsyncTasks.awaitIdle(taskExecutor);

    assertThat(unknown.getResponse().getStatus())
        .isEqualTo(known.getResponse().getStatus())
        .isEqualTo(204);
    assertThat(unknown.getResponse().getContentAsString())
        .isEqualTo(known.getResponse().getContentAsString());
    assertThat(unknown.getResponse().getContentType())
        .isEqualTo(known.getResponse().getContentType());
    assertThat(mail.sent())
        .extracting(RecordingMailSender.Sent::to)
        .containsExactly("alice@example.com");
    assertThat(jdbc.queryForObject("select count(*) from password_reset_tokens", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void bannedAndDeletedAccountsAlsoGetTheSameAnswerButNoEmail() throws Exception {
    User banned = createUser("mallory");
    banned.setStatus(UserStatus.BANNED);
    users.save(banned);
    createUser("gone");
    jdbc.update("update users set deleted_at = now() where username = 'gone'");

    assertThat(forgot("mallory@example.com").getResponse().getStatus()).isEqualTo(204);
    assertThat(forgot("gone@example.com").getResponse().getStatus()).isEqualTo(204);
    AsyncTasks.awaitIdle(taskExecutor);

    assertThat(mail.sent()).isEmpty();
    assertThat(jdbc.queryForObject("select count(*) from password_reset_tokens", Integer.class))
        .isZero();
  }

  @Test
  void aMalformedAddressIsRejectedWithAFieldError() throws Exception {
    for (String bad : new String[] {"", "not-an-email", "a".repeat(250) + "@example.com"}) {
      MvcResult result = forgot(bad);

      assertThat(result.getResponse().getStatus()).as(bad).isEqualTo(400);
      assertThat(result.getResponse().getContentAsString()).as(bad).contains("\"email\":[");
    }
  }

  @Test
  void aMailOutageStillGetsTheSameAnswer() throws Exception {
    createUser("alice");
    mail.setFailing(true);

    assertThat(forgot("alice@example.com").getResponse().getStatus()).isEqualTo(204);
    AsyncTasks.awaitIdle(taskExecutor);

    assertThat(mail.sent()).isEmpty();
  }

  @Test
  void askingAgainVoidsTheOlderLink() throws Exception {
    createUser("alice");
    String first = requestToken("alice@example.com");
    String second = requestToken("alice@example.com");

    assertThat(second).isNotEqualTo(first);
    assertThat(reset(first, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(400);
    assertThat(reset(second, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(204);
  }

  @Test
  void theLinkSetsANewPassword() throws Exception {
    createUser("alice");
    String token = requestToken("alice@example.com");

    MvcResult result = reset(token, NEW_PASSWORD);

    assertThat(result.getResponse().getStatus()).isEqualTo(204);
    assertThat(loginStatus("alice", NEW_PASSWORD)).isEqualTo(200);
    assertThat(loginStatus("alice", PASSWORD)).isEqualTo(401);
    assertThat(jdbc.queryForObject("select password_hash from users", String.class))
        .startsWith("{bcrypt}$2")
        .doesNotContain(NEW_PASSWORD);
  }

  @Test
  void aLinkWorksOnlyOnce() throws Exception {
    createUser("alice");
    String token = requestToken("alice@example.com");

    assertThat(reset(token, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(204);
    MvcResult again = reset(token, "someone else's choice");

    assertThat(again.getResponse().getStatus()).isEqualTo(400);
    assertThat(again.getResponse().getContentAsString())
        .contains("This password reset link is invalid or has expired.");
    assertThat(loginStatus("alice", NEW_PASSWORD)).isEqualTo(200);
  }

  @Test
  void unknownAndExpiredLinksAreRejectedTheSameWay() throws Exception {
    createUser("alice");
    String expired = requestToken("alice@example.com");
    jdbc.update("update password_reset_tokens set expires_at = now() - interval '1 second'");

    for (String token : new String[] {"nope", SecureTokens.generate(), expired}) {
      MvcResult result = reset(token, NEW_PASSWORD);
      assertThat(result.getResponse().getStatus()).as(token).isEqualTo(400);
      assertThat(result.getResponse().getContentAsString())
          .as(token)
          .contains("This password reset link is invalid or has expired.");
    }
    assertThat(loginStatus("alice", PASSWORD)).isEqualTo(200);
  }

  @Test
  void aLinkLastsOneHour() throws Exception {
    createUser("alice");
    requestToken("alice@example.com");

    Boolean aboutOneHour =
        jdbc.queryForObject(
            "select expires_at between now() + interval '59 minutes'"
                + " and now() + interval '61 minutes' from password_reset_tokens",
            Boolean.class);
    assertThat(aboutOneHour).isTrue();
  }

  @Test
  void aWeakPasswordIsRefusedAndTheLinkStaysUsable() throws Exception {
    createUser("alice");
    String token = requestToken("alice@example.com");

    MvcResult tooShort = reset(token, "short");
    MvcResult tooLong = reset(token, "é".repeat(37)); // 74 bytes

    for (MvcResult result : new MvcResult[] {tooShort, tooLong}) {
      assertThat(result.getResponse().getStatus()).isEqualTo(400);
      assertThat(result.getResponse().getContentAsString()).contains("\"password\":[");
    }
    assertThat(reset(token, "é".repeat(36)).getResponse().getStatus())
        .as("72 bytes fit")
        .isEqualTo(204);
  }

  @Test
  void resettingLogsTheUserOutEverywhereAndLeavesEveryoneElseAlone() throws Exception {
    createUser("alice");
    createUser("bob");
    Cookie laptop = session(login(mockMvc, "alice", PASSWORD, false));
    MvcResult phoneLogin = login(mockMvc, "alice", PASSWORD, true); // remembered
    Cookie phone = session(phoneLogin);
    Cookie remembered = lastCookie(phoneLogin, "remember-me");
    Cookie bob = session(login(mockMvc, "bob", PASSWORD, true));
    assertThat(meStatus(laptop)).isEqualTo(200);
    assertThat(meStatus(phone)).isEqualTo(200);
    String token = requestToken("alice@example.com");

    assertThat(reset(token, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(204);

    assertThat(meStatus(laptop)).as("alice's laptop session").isEqualTo(401);
    assertThat(meStatus(phone)).as("alice's phone session").isEqualTo(401);
    assertThat(meStatus(remembered)).as("alice's remembered login").isEqualTo(401);
    assertThat(count("spring_session where principal_name = 'alice'")).isZero();
    assertThat(count("persistent_logins where username = 'alice'")).isZero();
    assertThat(meStatus(bob)).as("bob's session").isEqualTo(200);
    assertThat(count("persistent_logins where username = 'bob'"))
        .as("bob's remembered login")
        .isEqualTo(1);
  }

  @Test
  void theEmailIsSentOnAnotherThreadSoTheAnswerTakesNoLongerForKnownAddresses() throws Exception {
    createUser("alice");

    requestToken("alice@example.com");

    assertThat(mail.last().thread()).isNotEqualTo(Thread.currentThread().getName());
  }

  private int count(String fromAndWhere) {
    return jdbc.queryForObject("select count(*) from " + fromAndWhere, Integer.class);
  }

  @Test
  void aBrowserThatIsRememberedWhileItResetsEndsUpLoggedOutToo() throws Exception {
    createUser("alice");
    Cookie remembered = lastCookie(login(mockMvc, "alice", PASSWORD, true), "remember-me");
    jdbc.update("delete from spring_session"); // the session expired; only the cookie is left
    String token = requestToken("alice@example.com");

    // The request itself brings the session back from the cookie before the reset runs.
    MvcResult result = reset(token, NEW_PASSWORD, remembered);

    assertThat(result.getResponse().getStatus()).isEqualTo(204);
    assertThat(count("spring_session where principal_name = 'alice'"))
        .as("no session of alice may survive, including the one this request started")
        .isZero();
    assertThat(meStatus(remembered)).as("the old remember-me cookie").isEqualTo(401);
    Cookie newSession = session(result);
    if (newSession != null) {
      assertThat(meStatus(newSession))
          .as("a session cookie this response may have set")
          .isEqualTo(401);
    }
    assertThat(count("persistent_logins where username = 'alice'")).isZero();
  }

  @Test
  void aBrowserWithALiveSessionEndsUpLoggedOutToo() throws Exception {
    createUser("alice");
    Cookie live = session(login(mockMvc, "alice", PASSWORD, false));
    String token = requestToken("alice@example.com");

    assertThat(reset(token, NEW_PASSWORD, live).getResponse().getStatus()).isEqualTo(204);

    assertThat(meStatus(live)).isEqualTo(401);
    assertThat(count("spring_session where principal_name = 'alice'")).isZero();
  }

  @Test
  void anAccountWithoutAPasswordCanSetOne() throws Exception {
    createUser("gina");
    jdbc.update("update users set password_hash = null"); // signed up with Google
    String token = requestToken("gina@example.com");

    assertThat(reset(token, NEW_PASSWORD).getResponse().getStatus()).isEqualTo(204);

    assertThat(loginStatus("gina", NEW_PASSWORD)).isEqualTo(200);
  }

  @Test
  void resettingDoesNotChangeWhetherTheEmailIsVerified() throws Exception {
    createUser("alice");
    createUser("vera");
    jdbc.update("update users set email_verified_at = now() where username = 'vera'");

    reset(requestToken("alice@example.com"), NEW_PASSWORD);
    reset(requestToken("vera@example.com"), NEW_PASSWORD);

    assertThat(
            jdbc.queryForList(
                "select username from users where email_verified_at is not null", String.class))
        .containsExactly("vera");
  }

  @Test
  void ofManyRequestsWithTheSameLinkExactlyOneWins() throws Exception {
    createUser("alice");
    String token = requestToken("alice@example.com");
    int attempts = 6;

    ExecutorService pool = Executors.newFixedThreadPool(attempts);
    CountDownLatch go = new CountDownLatch(1);
    List<Future<Integer>> statuses = new ArrayList<>();
    for (int i = 0; i < attempts; i++) {
      String password = "password number " + i;
      statuses.add(
          pool.submit(
              () -> {
                go.await();
                return reset(token, password).getResponse().getStatus();
              }));
    }
    go.countDown();
    List<Integer> results = new ArrayList<>();
    String winner = null;
    for (int i = 0; i < attempts; i++) {
      int status = statuses.get(i).get(30, TimeUnit.SECONDS);
      results.add(status);
      if (status == 204) {
        winner = "password number " + i;
      }
    }
    pool.shutdown();

    assertThat(results).containsOnlyOnce(204).filteredOn(status -> status != 204).containsOnly(400);
    assertThat(loginStatus("alice", winner))
        .as("the winner's password is the one that stuck")
        .isEqualTo(200);
  }

  @Test
  void bothEndpointsNeedACsrfToken() throws Exception {
    for (String path : new String[] {"/api/auth/forgot-password", "/api/auth/reset-password"}) {
      int status =
          mockMvc
              .perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
              .andReturn()
              .getResponse()
              .getStatus();
      assertThat(status).as(path).isEqualTo(403);
    }
  }

  @Test
  void theEndpointsAreDocumentedInOpenApi() throws Exception {
    mockMvc
        .perform(get("/api/v3/api-docs"))
        .andExpect(jsonPath("$.paths['/api/auth/forgot-password']").exists())
        .andExpect(jsonPath("$.paths['/api/auth/reset-password']").exists());
  }
}

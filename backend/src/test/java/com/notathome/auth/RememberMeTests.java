package com.notathome.auth;

import static com.notathome.support.Accounts.PASSWORD;
import static com.notathome.support.Accounts.lastCookie;
import static com.notathome.support.Accounts.login;
import static com.notathome.support.Accounts.session;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.notathome.support.Csrf;
import com.notathome.support.IntegrationTest;
import com.notathome.users.User;
import com.notathome.users.UserRepository;
import com.notathome.users.UserStatus;
import jakarta.servlet.http.Cookie;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@IntegrationTest
class RememberMeTests {

  static final String COOKIE = "remember-me";

  @Autowired MockMvc mockMvc;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void cleanUp() {
    jdbc.update("delete from persistent_logins");
    jdbc.update("delete from spring_session");
    jdbc.update("delete from users");
  }

  User createUser(String username) {
    return users.save(
        new User(username, username + "@example.com", username, passwordEncoder.encode(PASSWORD)));
  }

  /** The persisted state of every remembered device: series, token. */
  List<Map<String, Object>> rows() {
    return jdbc.queryForList("select username, series, token from persistent_logins");
  }

  static String seriesOf(Cookie rememberMe) {
    String decoded =
        new String(Base64.getDecoder().decode(rememberMe.getValue()), StandardCharsets.UTF_8);
    // Spring URL-encodes each part before joining them.
    return URLDecoder.decode(decoded.split(":")[0], StandardCharsets.UTF_8);
  }

  /**
   * Pretends the last rotation happened a minute ago, so the previous token is no longer accepted.
   */
  void pastTheGraceWindow() {
    jdbc.update("update persistent_logins set rotated_at = now() - interval '1 minute'");
  }

  MvcResult meWith(Cookie... cookies) throws Exception {
    return mockMvc.perform(get("/api/me").cookie(cookies)).andReturn();
  }

  @Test
  void rememberingALoginSetsA30DayCookieAndStoresTheToken() throws Exception {
    createUser("alice");

    MvcResult result = login(mockMvc, "alice", PASSWORD, true);

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    Cookie cookie = lastCookie(result, COOKIE);
    assertThat(cookie).isNotNull();
    assertThat(cookie.getMaxAge()).isEqualTo(30 * 24 * 60 * 60);
    assertThat(cookie.getPath()).isEqualTo("/");
    assertThat(rows()).hasSize(1);
    assertThat(rows().getFirst()).containsEntry("username", "alice");
    assertThat(rows().getFirst()).containsEntry("series", seriesOf(cookie));
  }

  @Test
  void aLoginWithoutTheOptionLeavesNothingBehind() throws Exception {
    createUser("alice");

    MvcResult result = login(mockMvc, "alice", PASSWORD, false);

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(lastCookie(result, COOKIE)).isNull();
    assertThat(rows()).isEmpty();
  }

  @Test
  void theOptionIsOptionalInTheRequestBody() throws Exception {
    createUser("alice");

    MvcResult result =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .with(Csrf.token(mockMvc))
                    .contentType("application/json")
                    .content("{\"login\":\"alice\",\"password\":\"" + PASSWORD + "\"}"))
            .andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(lastCookie(result, COOKIE)).isNull();
  }

  @Test
  void aClosedBrowserIsLoggedBackInFromTheCookieAlone() throws Exception {
    createUser("alice");
    Cookie remembered = lastCookie(login(mockMvc, "alice", PASSWORD, true), COOKIE);
    jdbc.update("delete from spring_session"); // the session cookie died with the browser

    MvcResult result = meWith(remembered);

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(result.getResponse().getContentAsString()).contains("\"username\":\"alice\"");
    assertThat(session(result)).as("a new session was started").isNotNull();
    assertThat(jdbc.queryForList("select principal_name from spring_session", String.class))
        .containsExactly("alice");
  }

  @Test
  void eachUseReplacesTheTokenButKeepsTheSeries() throws Exception {
    createUser("alice");
    Cookie first = lastCookie(login(mockMvc, "alice", PASSWORD, true), COOKIE);

    Cookie second = lastCookie(meWith(first), COOKIE);

    assertThat(second).isNotNull();
    assertThat(second.getValue()).isNotEqualTo(first.getValue());
    assertThat(seriesOf(second)).isEqualTo(seriesOf(first));
    assertThat(rows()).hasSize(1);
    assertThat(rows().getFirst()).containsEntry("series", seriesOf(second));
    assertThat(second.getMaxAge()).as("the 30 days start again").isEqualTo(30 * 24 * 60 * 60);
  }

  @Test
  void aLiveSessionDoesNotUseUpTheToken() throws Exception {
    createUser("alice");
    MvcResult loggedIn = login(mockMvc, "alice", PASSWORD, true);
    Cookie remembered = lastCookie(loggedIn, COOKIE);
    String tokenBefore = (String) rows().getFirst().get("token");

    // Many requests with a session and the cookie, as a browser sends them.
    for (int i = 0; i < 3; i++) {
      MvcResult result = meWith(session(loggedIn), remembered);
      assertThat(result.getResponse().getStatus()).isEqualTo(200);
      assertThat(lastCookie(result, COOKIE)).isNull();
    }

    assertThat(rows().getFirst()).containsEntry("token", tokenBefore);
  }

  @Test
  void theRestoredSessionNeverHoldsThePasswordHash() throws Exception {
    createUser("alice");
    Cookie remembered = lastCookie(login(mockMvc, "alice", PASSWORD, true), COOKIE);
    jdbc.update("delete from spring_session");

    meWith(remembered);

    assertThat(jdbc.queryForObject("select count(*) from spring_session", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from spring_session_attributes"
                    + " where encode(attribute_bytes, 'escape') like '%$2a$%'",
                Integer.class))
        .isZero();
  }

  @Test
  void anOldTokenPresentedAgainRevokesEveryRememberedLoginWithoutAnError() throws Exception {
    createUser("alice");
    Cookie stolen = lastCookie(login(mockMvc, "alice", PASSWORD, true), COOKIE);
    Cookie current = lastCookie(meWith(stolen), COOKIE); // the real owner comes back first
    login(mockMvc, "alice", PASSWORD, true); // and has another device remembered too
    assertThat(rows()).hasSize(2);
    pastTheGraceWindow();

    MvcResult replay = meWith(stolen); // then the thief tries the old value

    assertThat(replay.getResponse().getStatus()).isEqualTo(401);
    assertThat(lastCookie(replay, COOKIE).getMaxAge()).isZero();
    assertThat(rows()).as("all of alice's remembered logins are gone").isEmpty();
    assertThat(meWith(current).getResponse().getStatus()).isEqualTo(401);
  }

  @Test
  void theTokenBeforeTheLatestStaysValidForAShortWhile() throws Exception {
    createUser("alice");
    Cookie first = lastCookie(login(mockMvc, "alice", PASSWORD, true), COOKIE);
    Cookie second = lastCookie(meWith(first), COOKIE); // first tab: wins, rotates
    String currentToken = (String) rows().getFirst().get("token");

    MvcResult parallelTab = meWith(first); // second tab, sent the same cookie at the same time

    assertThat(parallelTab.getResponse().getStatus()).isEqualTo(200);
    assertThat(parallelTab.getResponse().getContentAsString()).contains("\"username\":\"alice\"");
    assertThat(lastCookie(parallelTab, COOKIE))
        .as("it gets a session but no new credential: the first response carries the cookie")
        .isNull();
    assertThat(rows()).as("nothing was revoked or rotated again").hasSize(1);
    assertThat(rows().getFirst()).containsEntry("token", currentToken);
    assertThat(meWith(second).getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  void theTokenBeforeTheLatestStopsWorkingAfterTheGraceWindow() throws Exception {
    createUser("alice");
    Cookie first = lastCookie(login(mockMvc, "alice", PASSWORD, true), COOKIE);
    meWith(first);
    pastTheGraceWindow();

    MvcResult replay = meWith(first);

    assertThat(replay.getResponse().getStatus()).isEqualTo(401);
    assertThat(lastCookie(replay, COOKIE).getMaxAge()).isZero();
    assertThat(rows()).isEmpty();
  }

  @Test
  void onlyOneGenerationBackIsAccepted() throws Exception {
    createUser("alice");
    Cookie first = lastCookie(login(mockMvc, "alice", PASSWORD, true), COOKIE);
    Cookie second = lastCookie(meWith(first), COOKIE);
    meWith(second); // third generation (second is still the "previous" one, but first is not)
    jdbc.update("delete from spring_session");
    assertThat(rows()).hasSize(1);

    MvcResult replay = meWith(first);

    assertThat(replay.getResponse().getStatus()).isEqualTo(401);
    assertThat(rows()).as("two generations old is theft").isEmpty();
  }

  @Test
  void aBrowserRestoringManyTabsAtOnceKeepsItsLogin() throws Exception {
    createUser("alice");
    Cookie remembered = lastCookie(login(mockMvc, "alice", PASSWORD, true), COOKIE);
    jdbc.update("delete from spring_session");
    int tabs = 8;

    ExecutorService pool = Executors.newFixedThreadPool(tabs);
    CountDownLatch go = new CountDownLatch(1);
    List<Future<Integer>> statuses = new ArrayList<>();
    for (int i = 0; i < tabs; i++) {
      statuses.add(
          pool.submit(
              () -> {
                go.await();
                return meWith(remembered).getResponse().getStatus();
              }));
    }
    go.countDown();
    for (Future<Integer> status : statuses) {
      assertThat(status.get(30, TimeUnit.SECONDS)).isEqualTo(200);
    }
    pool.shutdown();

    assertThat(rows()).as("still remembered, one device").hasSize(1);
    assertThat(jdbc.queryForObject("select count(*) from spring_session", Integer.class))
        .isEqualTo(tabs);
  }

  @Test
  void logoutForgetsTheDevice() throws Exception {
    createUser("alice");
    MvcResult loggedIn = login(mockMvc, "alice", PASSWORD, true);
    Cookie remembered = lastCookie(loggedIn, COOKIE);

    MvcResult logout =
        mockMvc
            .perform(
                post("/api/auth/logout")
                    .cookie(session(loggedIn), remembered)
                    .with(Csrf.token(mockMvc)))
            .andExpect(status().isNoContent())
            .andReturn();

    assertThat(lastCookie(logout, COOKIE).getMaxAge()).isZero();
    assertThat(rows()).isEmpty();
    assertThat(meWith(remembered).getResponse().getStatus()).isEqualTo(401);
  }

  @Test
  void logoutWorksEvenWhenOnlyTheRememberCookieIsLeft() throws Exception {
    createUser("alice");
    Cookie remembered = lastCookie(login(mockMvc, "alice", PASSWORD, true), COOKIE);
    jdbc.update("delete from spring_session");

    mockMvc
        .perform(post("/api/auth/logout").cookie(remembered).with(Csrf.token(mockMvc)))
        .andExpect(status().isNoContent());

    assertThat(rows()).isEmpty();
  }

  @Test
  void aCookieOlderThan30DaysIsRejectedAndCleared() throws Exception {
    createUser("alice");
    Cookie remembered = lastCookie(login(mockMvc, "alice", PASSWORD, true), COOKIE);
    jdbc.update("update persistent_logins set last_used = now() - interval '31 days'");

    MvcResult result = meWith(remembered);

    assertThat(result.getResponse().getStatus()).isEqualTo(401);
    assertThat(lastCookie(result, COOKIE).getMaxAge()).isZero();
  }

  @Test
  void aCookieJustInsideThe30DaysStillWorks() throws Exception {
    createUser("alice");
    Cookie remembered = lastCookie(login(mockMvc, "alice", PASSWORD, true), COOKIE);
    jdbc.update("update persistent_logins set last_used = now() - interval '29 days 23 hours'");

    assertThat(meWith(remembered).getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  void garbageCookiesAreRejectedAndClearedNotAnError() throws Exception {
    createUser("alice");
    String[] garbage = {
      "x",
      "not base64 !!",
      Base64.getEncoder().encodeToString("only-one-part".getBytes(StandardCharsets.UTF_8)),
      Base64.getEncoder().encodeToString("unknown-series:token".getBytes(StandardCharsets.UTF_8)),
      Base64.getEncoder().encodeToString("a:b:c".getBytes(StandardCharsets.UTF_8)),
    };
    for (String value : garbage) {
      MvcResult result = meWith(new Cookie(COOKIE, value));

      assertThat(result.getResponse().getStatus()).as(value).isEqualTo(401);
      assertThat(lastCookie(result, COOKIE).getMaxAge()).as(value).isZero();
    }
  }

  @Test
  void aBannedUsersCookieIsRejectedAndCleared() throws Exception {
    User user = createUser("alice");
    Cookie remembered = lastCookie(login(mockMvc, "alice", PASSWORD, true), COOKIE);
    jdbc.update("delete from spring_session");
    user.setStatus(UserStatus.BANNED);
    users.save(user);

    MvcResult result = meWith(remembered);

    assertThat(result.getResponse().getStatus()).isEqualTo(401);
    assertThat(lastCookie(result, COOKIE).getMaxAge()).isZero();
    assertThat(session(result)).isNull();
  }

  @Test
  void aBannedUserIsNotRememberedEvenWithTheRightPassword() throws Exception {
    User user = createUser("alice");
    user.setStatus(UserStatus.BANNED);
    users.save(user);

    MvcResult result = login(mockMvc, "alice", PASSWORD, true);

    assertThat(result.getResponse().getStatus()).isEqualTo(403);
    assertThat(lastCookie(result, COOKIE)).isNull();
    assertThat(rows()).isEmpty();
  }

  @Test
  void aDeletedUsersCookieIsRejected() throws Exception {
    createUser("alice");
    Cookie remembered = lastCookie(login(mockMvc, "alice", PASSWORD, true), COOKIE);
    jdbc.update("delete from spring_session");
    jdbc.update("update users set deleted_at = now()");

    MvcResult result = meWith(remembered);

    assertThat(result.getResponse().getStatus()).isEqualTo(401);
    assertThat(lastCookie(result, COOKIE).getMaxAge()).isZero();
  }

  @Test
  void eachRememberedDeviceHasItsOwnSeries() throws Exception {
    createUser("alice");

    Cookie phone = lastCookie(login(mockMvc, "alice", PASSWORD, true), COOKIE);
    Cookie laptop = lastCookie(login(mockMvc, "alice", PASSWORD, true), COOKIE);

    assertThat(seriesOf(phone)).isNotEqualTo(seriesOf(laptop));
    assertThat(rows()).hasSize(2);
    jdbc.update("delete from spring_session");
    assertThat(meWith(phone).getResponse().getStatus()).isEqualTo(200);
    assertThat(meWith(laptop).getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  void loggingInWithoutTheOptionDropsSomeoneElsesRememberCookie() throws Exception {
    createUser("alice");
    createUser("bob");
    Cookie alices = lastCookie(login(mockMvc, "alice", PASSWORD, true), COOKIE);
    jdbc.update("delete from spring_session"); // alice's browser was restarted...

    // ...and then bob logs in on the same browser without ticking the box. The cookie of alice
    // restores her session first and is rotated, so the response would carry a fresh copy.
    MvcResult bobsLogin =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .cookie(alices)
                    .with(Csrf.token(mockMvc))
                    .contentType("application/json")
                    .content("{\"login\":\"bob\",\"password\":\"" + PASSWORD + "\"}"))
            .andReturn();

    assertThat(bobsLogin.getResponse().getContentAsString()).contains("\"username\":\"bob\"");
    assertThat(lastCookie(bobsLogin, COOKIE)).as("the last word is to clear it").isNotNull();
    assertThat(lastCookie(bobsLogin, COOKIE).getMaxAge()).isZero();
  }

  @Test
  void guestsAreNotAffected() throws Exception {
    mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/info"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").exists());
    assertThat(jdbc.queryForObject("select count(*) from spring_session", Integer.class)).isZero();
  }
}

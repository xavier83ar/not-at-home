package com.notathome.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.notathome.TestcontainersConfiguration;
import com.notathome.support.Csrf;
import com.notathome.users.User;
import com.notathome.users.UserRepository;
import com.notathome.users.UserRole;
import com.notathome.users.UserStatus;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationTests {

  static final String PASSWORD = "correct horse";

  @Autowired MockMvc mockMvc;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void cleanDatabase() {
    jdbc.update("delete from spring_session");
    jdbc.update("delete from users");
  }

  User createUser(String username, String email) {
    return users.save(new User(username, email, username, passwordEncoder.encode(PASSWORD)));
  }

  static String loginJson(String login, String password) {
    return "{\"login\":\"%s\",\"password\":\"%s\"}".formatted(login, password);
  }

  /** A login done the way the browser does it: CSRF cookie first, then the token in a header. */
  MvcResult login(String login, String password) throws Exception {
    return mockMvc
        .perform(
            post("/api/auth/login")
                .with(Csrf.token(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(login, password)))
        .andReturn();
  }

  Cookie sessionOf(MvcResult loginResult) {
    return lastCookie(loginResult, "SESSION");
  }

  /** Like a browser: when a response sets the same cookie twice, the last one wins. */
  static Cookie lastCookie(MvcResult result, String name) {
    Cookie found = null;
    for (Cookie cookie : result.getResponse().getCookies()) {
      if (cookie.getName().equals(name)) {
        found = cookie;
      }
    }
    return found;
  }

  @Test
  void csrfEndpointIssuesTheCookieForJavaScript() throws Exception {
    mockMvc
        .perform(get("/api/csrf"))
        .andExpect(status().isNoContent())
        .andExpect(cookie().exists("XSRF-TOKEN"))
        .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
        .andExpect(cookie().attribute("XSRF-TOKEN", "SameSite", "Lax"));
  }

  @Test
  void loginWithoutCsrfTokenIsForbidden() throws Exception {
    createUser("alice", "alice@example.com");
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("alice", PASSWORD)))
        .andExpect(status().isForbidden());
  }

  @Test
  void loginWithUsernameStartsASessionThatMeUnderstands() throws Exception {
    createUser("alice", "alice@example.com");

    MvcResult result = login("alice", PASSWORD);

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(result.getResponse().getContentAsString())
        .contains("\"username\":\"alice\"", "\"role\":\"USER\"", "\"emailVerified\":false")
        .doesNotContain("password");
    Cookie session = sessionOf(result);
    assertThat(session).isNotNull();

    mockMvc
        .perform(get("/api/me").cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("alice"))
        .andExpect(jsonPath("$.email").value("alice@example.com"))
        .andExpect(jsonPath("$.displayName").value("alice"));
  }

  @Test
  void loginAcceptsEmailAndIgnoresCase() throws Exception {
    createUser("alice", "alice@example.com");

    assertThat(login("ALICE@Example.com", PASSWORD).getResponse().getStatus()).isEqualTo(200);
    assertThat(login("Alice", PASSWORD).getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  void loginRotatesTheCsrfCookie() throws Exception {
    createUser("alice", "alice@example.com");
    Cookie before =
        mockMvc.perform(get("/api/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");

    MvcResult result =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .cookie(before)
                    .header("X-XSRF-TOKEN", before.getValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginJson("alice", PASSWORD)))
            .andExpect(status().isOk())
            .andReturn();

    Cookie after = lastCookie(result, "XSRF-TOKEN");
    assertThat(after).isNotNull();
    assertThat(after.getValue()).isNotEmpty().isNotEqualTo(before.getValue());
  }

  @Test
  void sessionIsStoredInPostgresUnderTheUsername() throws Exception {
    createUser("alice", "alice@example.com");

    login("alice", PASSWORD);

    assertThat(jdbc.queryForList("select principal_name from spring_session", String.class))
        .containsExactly("alice");
  }

  @Test
  void wrongPasswordAndUnknownUserGetTheSameProblemDetail() throws Exception {
    createUser("alice", "alice@example.com");

    MvcResult wrongPassword = login("alice", "nope-nope-nope");
    MvcResult unknownUser = login("nobody", PASSWORD);

    for (MvcResult result : new MvcResult[] {wrongPassword, unknownUser}) {
      assertThat(result.getResponse().getStatus()).isEqualTo(401);
      assertThat(result.getResponse().getContentType()).contains("application/problem+json");
      assertThat(result.getResponse().getContentAsString())
          .contains("Invalid username or password");
      assertThat(sessionOf(result)).isNull();
    }
  }

  @Test
  void bannedUserCannotLogIn() throws Exception {
    User user = createUser("alice", "alice@example.com");
    user.setStatus(UserStatus.BANNED);
    users.save(user);

    assertThat(login("alice", PASSWORD).getResponse().getStatus()).isEqualTo(401);
  }

  @Test
  void deletedUserCannotLogIn() throws Exception {
    createUser("alice", "alice@example.com");
    jdbc.update("update users set deleted_at = now()");

    assertThat(login("alice", PASSWORD).getResponse().getStatus()).isEqualTo(401);
  }

  @Test
  void overlongPasswordIsRejectedNotACrash() throws Exception {
    createUser("alice", "alice@example.com");

    // 72 characters but 144 bytes, which is more than bcrypt reads.
    assertThat(login("alice", "é".repeat(72)).getResponse().getStatus()).isEqualTo(401);
  }

  @Test
  void blankFieldsReturnFieldErrors() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/login")
                .with(Csrf.token(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("", "")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Validation failed"))
        .andExpect(jsonPath("$.errors.login").isNotEmpty())
        .andExpect(jsonPath("$.errors.password").isNotEmpty());
  }

  @Test
  void meRequiresALogin() throws Exception {
    mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void anonymousRequestsDoNotCreateSessions() throws Exception {
    mockMvc
        .perform(get("/api/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().doesNotExist("Set-Cookie"));

    assertThat(jdbc.queryForObject("select count(*) from spring_session", Integer.class)).isZero();
  }

  @Test
  void meDropsTheSessionOfAUserBannedAfterLogin() throws Exception {
    User user = createUser("alice", "alice@example.com");
    Cookie session = sessionOf(login("alice", PASSWORD));
    mockMvc.perform(get("/api/me").cookie(session)).andExpect(status().isOk());

    user.setStatus(UserStatus.BANNED);
    users.save(user);

    mockMvc.perform(get("/api/me").cookie(session)).andExpect(status().isUnauthorized());
    assertThat(jdbc.queryForObject("select count(*) from spring_session", Integer.class)).isZero();
  }

  @Test
  void roleComesFromTheDatabase() throws Exception {
    User user = createUser("root", "root@example.com");
    user.setRole(UserRole.ADMIN);
    users.save(user);

    Cookie session = sessionOf(login("root", PASSWORD));

    mockMvc.perform(get("/api/me").cookie(session)).andExpect(jsonPath("$.role").value("ADMIN"));
  }

  @Test
  void logoutEndsTheSessionAndClearsTheCsrfCookie() throws Exception {
    createUser("alice", "alice@example.com");
    MvcResult loggedIn = login("alice", PASSWORD);
    Cookie session = sessionOf(loggedIn);
    Cookie xsrf = lastCookie(loggedIn, "XSRF-TOKEN");

    mockMvc
        .perform(
            post("/api/auth/logout").cookie(session, xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
        .andExpect(status().isNoContent())
        .andExpect(cookie().maxAge("XSRF-TOKEN", 0));

    mockMvc.perform(get("/api/me").cookie(session)).andExpect(status().isUnauthorized());
    assertThat(jdbc.queryForObject("select count(*) from spring_session", Integer.class)).isZero();
  }

  @Test
  void logoutWhenNotLoggedInIsHarmless() throws Exception {
    mockMvc
        .perform(post("/api/auth/logout").with(Csrf.token(mockMvc)))
        .andExpect(status().isNoContent());
  }

  @Test
  void usernameAndEmailAreUniqueIgnoringCase() {
    createUser("alice", "alice@example.com");

    assertThatThrownBy(() -> createUser("ALICE", "other@example.com"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> createUser("bob", "Alice@Example.com"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void openApiDocumentsTheAuthEndpoints() throws Exception {
    mockMvc
        .perform(get("/api/v3/api-docs"))
        .andExpect(jsonPath("$.paths['/api/auth/login']").exists())
        .andExpect(jsonPath("$.paths['/api/auth/logout']").exists())
        .andExpect(jsonPath("$.paths['/api/me']").exists())
        .andExpect(jsonPath("$.paths['/api/csrf']").exists());
  }
}

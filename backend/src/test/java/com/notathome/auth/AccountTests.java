package com.notathome.auth;

import static com.notathome.support.Accounts.PASSWORD;
import static com.notathome.support.Accounts.login;
import static com.notathome.support.Accounts.register;
import static com.notathome.support.Accounts.session;
import static com.notathome.support.Forum.reply;
import static com.notathome.support.Forum.send;
import static com.notathome.support.Forum.topic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.notathome.categories.AdminCategoryService;
import com.notathome.support.Csrf;
import com.notathome.support.Forum;
import com.notathome.support.IntegrationTest;
import com.notathome.support.RecordingMailSender;
import com.notathome.users.User;
import com.notathome.users.UserRepository;
import com.notathome.users.UserRole;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Password, email and deletion of one's own account (FR-8.3 to FR-8.5). */
@IntegrationTest
class AccountTests {

  static final String NEW_PASSWORD = "a brand new password";

  @Autowired MockMvc mockMvc;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder passwordEncoder;
  @Autowired AdminCategoryService categories;
  @Autowired JdbcTemplate jdbc;
  @Autowired RecordingMailSender mail;

  private Long categoryId;

  @BeforeEach
  void setUp() {
    Forum.reset(jdbc);
    mail.reset();
    categoryId = categories.create("Hiking", "Trails", null).getId();
  }

  @AfterEach
  void tearDown() {
    Forum.reset(jdbc);
  }

  // --- change password (FR-8.3)

  @Test
  void aUserChangesTheirPasswordWithTheCurrentOne() throws Exception {
    Cookie alice = account("alice", UserRole.USER, true);

    changePassword(alice, PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

    assertThat(login(mockMvc, "alice", NEW_PASSWORD, false).getResponse().getStatus())
        .isEqualTo(200);
    assertThat(login(mockMvc, "alice", PASSWORD, false).getResponse().getStatus()).isEqualTo(401);
  }

  @Test
  void aWrongCurrentPasswordChangesNothing() throws Exception {
    Cookie alice = account("alice", UserRole.USER, true);

    changePassword(alice, "not my password", NEW_PASSWORD)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.currentPassword[0]").value("is incorrect"));

    assertThat(login(mockMvc, "alice", PASSWORD, false).getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  void theNewPasswordFollowsTheRegistrationRules() throws Exception {
    Cookie alice = account("alice", UserRole.USER, true);

    changePassword(alice, PASSWORD, "short")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.newPassword[0]").value("must be at least 8 characters"));
    changePassword(alice, PASSWORD, "é".repeat(37))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.newPassword").isNotEmpty());
    send(mockMvc, HttpMethod.PUT, alice, "/api/me/password", "{\"newPassword\":\"long enough\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.currentPassword").isNotEmpty());
  }

  @Test
  void changingThePasswordEndsOtherSessionsAndRememberedLoginsButNotThisOne() throws Exception {
    Cookie here = account("alice", UserRole.USER, true);
    Cookie elsewhere = session(login(mockMvc, "alice", PASSWORD, true));
    assertThat(jdbc.queryForObject("select count(*) from persistent_logins", Integer.class))
        .isEqualTo(1);

    changePassword(here, PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

    mockMvc.perform(get("/api/me").cookie(here)).andExpect(status().isOk());
    mockMvc.perform(get("/api/me").cookie(elsewhere)).andExpect(status().isUnauthorized());
    assertThat(jdbc.queryForObject("select count(*) from persistent_logins", Integer.class))
        .isZero();
  }

  @Test
  void changingThePasswordVoidsResetLinksAlreadyAskedFor() throws Exception {
    Cookie alice = account("alice", UserRole.USER, true);
    jdbc.update(
        "insert into password_reset_tokens (user_id, token_hash, expires_at)"
            + " select id, repeat('a', 64), now() + interval '1 hour' from users");

    changePassword(alice, PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

    assertThat(jdbc.queryForObject("select count(*) from password_reset_tokens", Integer.class))
        .isZero();
  }

  @Test
  void changingThePasswordNeedsALoginAndTheCsrfToken() throws Exception {
    Cookie alice = account("alice", UserRole.USER, true);
    String body =
        "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}";

    mockMvc
        .perform(
            put("/api/me/password")
                .with(Csrf.token(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            put("/api/me/password")
                .cookie(alice)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
  }

  // --- change email (FR-8.3)

  @Test
  void theNewAddressIsOnlyUsedOnceItIsVerified() throws Exception {
    Cookie alice = account("alice", UserRole.USER, false);

    requestEmail(alice, PASSWORD, "alice.new@example.com").andExpect(status().isNoContent());

    // The link goes to the new address; until it is used the old one is still the account's.
    assertThat(mail.sent()).hasSize(1);
    assertThat(mail.last().to()).isEqualTo("alice.new@example.com");
    assertThat(mail.last().subject()).isEqualTo("Confirm your new email address");
    mockMvc
        .perform(get("/api/me").cookie(alice))
        .andExpect(jsonPath("$.email").value("alice@example.com"))
        .andExpect(jsonPath("$.pendingEmail").value("alice.new@example.com"))
        .andExpect(jsonPath("$.emailVerified").value(false));
    assertThat(login(mockMvc, "alice@example.com", PASSWORD, false).getResponse().getStatus())
        .isEqualTo(200);

    verify(mail.last().verificationToken()).andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/me").cookie(alice))
        .andExpect(jsonPath("$.email").value("alice.new@example.com"))
        .andExpect(jsonPath("$.pendingEmail").doesNotExist())
        .andExpect(jsonPath("$.emailVerified").value(true));
    assertThat(login(mockMvc, "alice.new@example.com", PASSWORD, false).getResponse().getStatus())
        .isEqualTo(200);
    assertThat(login(mockMvc, "alice@example.com", PASSWORD, false).getResponse().getStatus())
        .isEqualTo(401);
  }

  @Test
  void aChangeLinkWorksOnlyOnce() throws Exception {
    Cookie alice = account("alice", UserRole.USER, true);
    requestEmail(alice, PASSWORD, "alice.new@example.com").andExpect(status().isNoContent());
    String token = mail.last().verificationToken();

    verify(token).andExpect(status().isNoContent());
    verify(token).andExpect(status().isBadRequest());
  }

  @Test
  void aNewRequestReplacesTheEarlierOne() throws Exception {
    Cookie alice = account("alice", UserRole.USER, true);
    requestEmail(alice, PASSWORD, "first@example.com").andExpect(status().isNoContent());
    String first = mail.last().verificationToken();
    requestEmail(alice, PASSWORD, "second@example.com").andExpect(status().isNoContent());

    verify(first).andExpect(status().isBadRequest());
    verify(mail.last().verificationToken()).andExpect(status().isNoContent());
    mockMvc
        .perform(get("/api/me").cookie(alice))
        .andExpect(jsonPath("$.email").value("second@example.com"));
  }

  @Test
  void anEmailChangeNeedsTheCurrentPasswordAndAFreeAddress() throws Exception {
    Cookie alice = account("alice", UserRole.USER, true);
    account("bob", UserRole.USER, true);

    requestEmail(alice, "not my password", "new@example.com")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.currentPassword[0]").value("is incorrect"));
    requestEmail(alice, PASSWORD, "Alice@Example.com")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.email[0]").value("is already your email address"));
    requestEmail(alice, PASSWORD, "BOB@example.com")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.email[0]").value("is already registered"));
    requestEmail(alice, PASSWORD, "not an address")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.email[0]").value("must be a valid email address"));

    assertThat(mail.sent()).isEmpty();
    mockMvc
        .perform(get("/api/me").cookie(alice))
        .andExpect(jsonPath("$.pendingEmail").doesNotExist());
  }

  @Test
  void anAddressTakenBeforeTheLinkIsUsedIsRefused() throws Exception {
    Cookie alice = account("alice", UserRole.USER, true);
    requestEmail(alice, PASSWORD, "wanted@example.com").andExpect(status().isNoContent());
    users.save(new User("carol", "wanted@example.com", "Carol", "x"));

    verify(mail.last().verificationToken())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("That address now belongs to another account."));

    mockMvc
        .perform(get("/api/me").cookie(alice))
        .andExpect(jsonPath("$.email").value("alice@example.com"));
  }

  @Test
  void anOrdinaryVerificationLinkStillRefusesAnAddressThatChanged() throws Exception {
    // Not a change request: the address was swapped behind the link's back.
    Cookie alice = session(register(mockMvc, "alicia", "alicia@example.com", PASSWORD));
    String token = mail.last().verificationToken();
    jdbc.update("update users set email = 'other@example.com'");

    verify(token).andExpect(status().isBadRequest());
    mockMvc
        .perform(get("/api/me").cookie(alice))
        .andExpect(jsonPath("$.emailVerified").value(false));
  }

  @Test
  void aChangeNeedsALogin() throws Exception {
    mockMvc
        .perform(
            post("/api/me/email-change")
                .with(Csrf.token(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"x\",\"email\":\"a@b.co\"}"))
        .andExpect(status().isUnauthorized());
  }

  // --- delete account (FR-8.4, FR-8.5)

  @Test
  void deletingNeedsThePassword() throws Exception {
    Cookie alice = account("alice", UserRole.USER, true);

    deleteAccount(alice, "not my password")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.password[0]").value("is incorrect"));

    mockMvc.perform(get("/api/me").cookie(alice)).andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from users where deleted_at is null", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void deletingAnAccountAnonymizesItAndDeletesItsPostsByTheRulesOfFr53() throws Exception {
    Cookie alice = account("alice", UserRole.USER, true);
    Cookie bob = account("bob", UserRole.USER, true);
    Long aliceId = id("alice");
    // Alice's topic that nobody replied to: goes entirely.
    Long lonely = topic(mockMvc, alice, categoryId, "Lonely");
    // Her topic with a reply from Bob: stays, opening post removed.
    Long answered = topic(mockMvc, alice, categoryId, "Alice started this");
    reply(mockMvc, bob, answered, "Bob replies");
    // Bob's topic where she replied: stays, only her reply goes.
    Long bobs = topic(mockMvc, bob, categoryId, "Bob's topic");
    reply(mockMvc, alice, bobs, "Alice replies");
    reply(mockMvc, bob, bobs, "Bob again");
    jdbc.update(
        "update users set avatar_url = '/api/uploads/x.png', google_id = 'google-123',"
            + " profile_content = '{\"blocks\":[]}'::jsonb where id = ?",
        aliceId);
    Cookie elsewhere = session(login(mockMvc, "alice", PASSWORD, true));
    requestEmail(alice, PASSWORD, "alice.new@example.com").andExpect(status().isNoContent());

    deleteAccount(alice, PASSWORD).andExpect(status().isNoContent());

    Map<String, Object> row = jdbc.queryForMap("select * from users where id = ?", aliceId);
    assertThat(row.get("deleted_at")).isNotNull();
    assertThat(row.get("username")).isEqualTo("deleted:" + aliceId);
    assertThat(row.get("email")).isEqualTo("deleted-" + aliceId + "@deleted.invalid");
    assertThat(row.get("display_name")).isEqualTo("Deleted user");
    assertThat(row.get("password_hash")).isNull();
    assertThat(row.get("google_id")).isNull();
    assertThat(row.get("avatar_url")).isNull();
    assertThat(row.get("profile_content")).isNull();
    assertThat(row.get("email_verified_at")).isNull();

    assertThat(topicRemoved(lonely)).isTrue();
    assertThat(topicRemoved(answered)).isFalse();
    assertThat(topicReplies(answered)).isEqualTo(1);
    assertThat(topicRemoved(bobs)).isFalse();
    assertThat(topicReplies(bobs)).isEqualTo(1);
    mockMvc
        .perform(get("/api/topics/" + answered))
        .andExpect(jsonPath("$.posts.items[0].removed").value(true))
        .andExpect(jsonPath("$.posts.items[0].author.displayName").value("Deleted user"))
        .andExpect(jsonPath("$.posts.items[0].author.deleted").value(true))
        .andExpect(jsonPath("$.posts.items[1].author.username").value("bob"));
    mockMvc.perform(get("/api/users/alice")).andExpect(status().isNotFound());
    mockMvc.perform(get("/api/users/deleted:" + aliceId)).andExpect(status().isNotFound());

    // Logged out everywhere, and the tokens that held an address are gone.
    mockMvc.perform(get("/api/me").cookie(alice)).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/me").cookie(elsewhere)).andExpect(status().isUnauthorized());
    assertThat(login(mockMvc, "alice", PASSWORD, false).getResponse().getStatus()).isEqualTo(401);
    assertThat(jdbc.queryForObject("select count(*) from persistent_logins", Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from email_verification_tokens where user_id = ?",
                Integer.class,
                aliceId))
        .isZero();

    // The same username and email can be used again (FR-8.4).
    assertThat(register(mockMvc, "alice", "alice@example.com", PASSWORD).getResponse().getStatus())
        .isEqualTo(201);
  }

  @Test
  void deletingAnAccountClearsThisBrowsersCookies() throws Exception {
    Cookie alice = account("alice", UserRole.USER, true);
    Cookie remembered = session(login(mockMvc, "alice", PASSWORD, true));

    var result = deleteAccount(remembered, PASSWORD).andExpect(status().isNoContent()).andReturn();

    assertThat(result.getResponse().getCookies())
        .anySatisfy(
            cookie -> {
              assertThat(cookie.getName()).isEqualTo("remember-me");
              assertThat(cookie.getMaxAge()).isZero();
            });
    mockMvc.perform(get("/api/me").cookie(alice)).andExpect(status().isUnauthorized());
  }

  @Test
  void deletingAnAccountLeavesOtherPeoplesTopicsAndCountsConsistent() throws Exception {
    Cookie alice = account("alice", UserRole.USER, true);
    Cookie bob = account("bob", UserRole.USER, true);
    Long bobs = topic(mockMvc, bob, categoryId, "Bob's topic");
    reply(mockMvc, alice, bobs, "Alice replies");
    assertThat(topicReplies(bobs)).isEqualTo(1);

    deleteAccount(alice, PASSWORD).andExpect(status().isNoContent());

    assertThat(topicReplies(bobs)).isZero();
    mockMvc
        .perform(get("/api/users/bob"))
        .andExpect(jsonPath("$.postCount").value(1))
        .andExpect(jsonPath("$.recentPosts.length()").value(1));
    mockMvc
        .perform(get("/api/categories/hiking"))
        .andExpect(jsonPath("$.topicCount").value(1))
        .andExpect(jsonPath("$.postCount").value(1));
    mockMvc
        .perform(get("/api/home"))
        .andExpect(jsonPath("$.activeUsers.length()").value(1))
        .andExpect(jsonPath("$.activeUsers[0].user.username").value("bob"));
  }

  @Test
  void theLastAdminCannotDeleteTheirAccount() throws Exception {
    Cookie root = account("root", UserRole.ADMIN, true);

    deleteAccount(root, PASSWORD)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("LAST_ADMIN"));

    mockMvc.perform(get("/api/me").cookie(root)).andExpect(status().isOk());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from users where deleted_at is null", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void anAdminCanDeleteTheirAccountWhenAnotherAdminRemains() throws Exception {
    Cookie root = account("root", UserRole.ADMIN, true);
    account("second", UserRole.ADMIN, true);

    deleteAccount(root, PASSWORD).andExpect(status().isNoContent());
  }

  @Test
  void aBannedAdminDoesNotCountAsAnotherAdmin() throws Exception {
    Cookie root = account("root", UserRole.ADMIN, true);
    account("second", UserRole.ADMIN, true);
    jdbc.update("update users set status = 'BANNED' where username = 'second'");

    deleteAccount(root, PASSWORD).andExpect(status().isConflict());
  }

  @Test
  void deletingNeedsALogin() throws Exception {
    mockMvc
        .perform(
            post("/api/me/delete-account")
                .with(Csrf.token(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"x\"}"))
        .andExpect(status().isUnauthorized());
  }

  // --- helpers

  private Cookie account(String username, UserRole role, boolean verified) throws Exception {
    return Forum.userSession(mockMvc, users, passwordEncoder, username, role, verified);
  }

  private Long id(String username) {
    return jdbc.queryForObject("select id from users where username = ?", Long.class, username);
  }

  private boolean topicRemoved(Long topicId) {
    return jdbc.queryForObject(
        "select deleted_at is not null from topics where id = ?", Boolean.class, topicId);
  }

  private int topicReplies(Long topicId) {
    return jdbc.queryForObject(
        "select reply_count from topics where id = ?", Integer.class, topicId);
  }

  private ResultActions changePassword(Cookie who, String current, String next) throws Exception {
    return send(
        mockMvc,
        HttpMethod.PUT,
        who,
        "/api/me/password",
        "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(current, next));
  }

  private ResultActions requestEmail(Cookie who, String password, String email) throws Exception {
    return send(
        mockMvc,
        HttpMethod.POST,
        who,
        "/api/me/email-change",
        "{\"currentPassword\":\"%s\",\"email\":\"%s\"}".formatted(password, email));
  }

  private ResultActions deleteAccount(Cookie who, String password) throws Exception {
    return send(
        mockMvc,
        HttpMethod.POST,
        who,
        "/api/me/delete-account",
        "{\"password\":\"%s\"}".formatted(password));
  }

  private ResultActions verify(String token) throws Exception {
    return mockMvc.perform(
        post("/api/auth/verify-email")
            .with(Csrf.token(mockMvc))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"" + token + "\"}"));
  }
}

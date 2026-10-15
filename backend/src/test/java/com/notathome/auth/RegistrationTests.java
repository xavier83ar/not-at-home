package com.notathome.auth;

import static com.notathome.support.Accounts.PASSWORD;
import static com.notathome.support.Accounts.register;
import static com.notathome.support.Accounts.session;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.notathome.support.Csrf;
import com.notathome.support.IntegrationTest;
import com.notathome.support.RecordingMailSender;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@IntegrationTest
class RegistrationTests {

  @Autowired MockMvc mockMvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired RecordingMailSender mail;

  @BeforeEach
  void cleanUp() {
    jdbc.update("delete from spring_session");
    jdbc.update("delete from users");
    mail.reset();
  }

  @Test
  void registrationCreatesAnUnverifiedAccountAndLogsItIn() throws Exception {
    MvcResult result = register(mockMvc, "alice", "Alice B.", "alice@example.com", PASSWORD);

    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    assertThat(result.getResponse().getContentAsString())
        .contains(
            "\"username\":\"alice\"",
            "\"displayName\":\"Alice B.\"",
            "\"email\":\"alice@example.com\"",
            "\"role\":\"USER\"",
            "\"emailVerified\":false")
        .doesNotContain("password");

    Cookie session = session(result);
    assertThat(session).isNotNull();
    mockMvc
        .perform(get("/api/me").cookie(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("alice"));
  }

  @Test
  void passwordIsStoredHashedAndCanBeUsedToLogIn() throws Exception {
    register(mockMvc, "alice", "alice@example.com", PASSWORD);

    String stored = jdbc.queryForObject("select password_hash from users", String.class);
    assertThat(stored).startsWith("{bcrypt}$2").doesNotContain(PASSWORD);

    MvcResult login =
        mockMvc
            .perform(
                post("/api/auth/login")
                    .with(Csrf.token(mockMvc))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"login\":\"ALICE@example.com\",\"password\":\"" + PASSWORD + "\"}"))
            .andReturn();
    assertThat(login.getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  void thePasswordHashNeverReachesTheSessionTable() throws Exception {
    register(mockMvc, "alice", "alice@example.com", PASSWORD); // session started by registration
    mockMvc
        .perform(
            post("/api/auth/login") // and one started by login
                .with(Csrf.token(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"alice\",\"password\":\"" + PASSWORD + "\"}"))
        .andExpect(status().isOk());

    assertThat(jdbc.queryForObject("select count(*) from spring_session", Integer.class))
        .isEqualTo(2);
    Integer leaks =
        jdbc.queryForObject(
            "select count(*) from spring_session_attributes"
                + " where encode(attribute_bytes, 'escape') like '%$2a$%'",
            Integer.class);
    assertThat(leaks).isZero();
  }

  @Test
  void aVerificationEmailIsSentAfterRegistering() throws Exception {
    register(mockMvc, "alice", "Alice", "alice@example.com", PASSWORD);

    assertThat(mail.sent()).hasSize(1);
    RecordingMailSender.Sent email = mail.last();
    assertThat(email.to()).isEqualTo("alice@example.com");
    assertThat(email.from()).isEqualTo("noreply@notathome.local");
    assertThat(email.subject()).isEqualTo("Verify your email address");
    assertThat(email.text()).contains("Hi Alice", "http://localhost:3000/verify-email?token=");
    assertThat(email.html()).contains("cid:logo", "Verify my email");
    assertThat(email.verificationToken()).hasSize(43);
  }

  @Test
  void theTokenIsStoredOnlyAsAHash() throws Exception {
    register(mockMvc, "alice", "alice@example.com", PASSWORD);

    String token = mail.last().verificationToken();
    String stored =
        jdbc.queryForObject("select token_hash from email_verification_tokens", String.class);
    assertThat(stored).hasSize(64).isNotEqualTo(token).isEqualTo(SecureTokens.hash(token));
  }

  @Test
  void aDisplayNameCannotInjectHtmlIntoTheEmail() throws Exception {
    register(mockMvc, "eve", "<i>Eve</i>", "eve@example.com", PASSWORD);

    assertThat(mail.last().html()).contains("&lt;i&gt;Eve&lt;/i&gt;").doesNotContain("<i>Eve");
  }

  @Test
  void registrationStillWorksWhenTheMailServerIsDown() throws Exception {
    mail.setFailing(true);

    MvcResult result = register(mockMvc, "alice", "alice@example.com", PASSWORD);

    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    assertThat(mail.sent()).isEmpty();
    assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(1);
  }

  @Test
  void invalidFieldsAreReportedTogether() throws Exception {
    MvcResult result = register(mockMvc, "a!", " ", "not-an-email", "short");

    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(result.getResponse().getContentType()).contains("application/problem+json");
    String body = result.getResponse().getContentAsString();
    assertThat(body)
        .contains("\"detail\":\"Validation failed\"")
        .contains("\"username\":[")
        .contains("\"displayName\":[")
        .contains("\"email\":[")
        .contains("\"password\":[");
    assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isZero();
    assertThat(mail.sent()).isEmpty();
  }

  @Test
  void usernameRulesAreEnforced() throws Exception {
    for (String bad : new String[] {"ab", "x".repeat(31), "has space", "dot.dot", "ñandú"}) {
      MvcResult result = register(mockMvc, bad, "alice@example.com", PASSWORD);
      assertThat(result.getResponse().getStatus()).as(bad).isEqualTo(400);
      assertThat(result.getResponse().getContentAsString()).as(bad).contains("\"username\":[");
    }
    for (String good : new String[] {"abc", "x".repeat(30), "Mixed_Case-1"}) {
      MvcResult result = register(mockMvc, good, good + "@example.com", PASSWORD);
      assertThat(result.getResponse().getStatus()).as(good).isEqualTo(201);
    }
  }

  @Test
  void passwordMustBeAtLeastEightCharacters() throws Exception {
    assertThat(register(mockMvc, "alice", "a@example.com", "1234567").getResponse().getStatus())
        .isEqualTo(400);
    assertThat(register(mockMvc, "alice", "a@example.com", "12345678").getResponse().getStatus())
        .isEqualTo(201);
  }

  @Test
  void passwordIsLimitedToWhatBcryptReadsInBytes() throws Exception {
    // "é" is two bytes: 36 of them fit in bcrypt's 72, 37 would be silently cut short.
    MvcResult tooLong = register(mockMvc, "bob", "bob@example.com", "é".repeat(37));
    assertThat(tooLong.getResponse().getStatus()).isEqualTo(400);
    assertThat(tooLong.getResponse().getContentAsString()).contains("\"password\":[");

    MvcResult fits = register(mockMvc, "alice", "alice@example.com", "é".repeat(36));
    assertThat(fits.getResponse().getStatus()).isEqualTo(201);
  }

  @Test
  void usernameAndEmailMustBeUniqueIgnoringCase() throws Exception {
    register(mockMvc, "Alice", "alice@example.com", PASSWORD);
    mail.reset();

    MvcResult sameUsername = register(mockMvc, "ALICE", "other@example.com", PASSWORD);
    MvcResult sameEmail = register(mockMvc, "bob", "Alice@Example.COM", PASSWORD);
    MvcResult both = register(mockMvc, "alice", "ALICE@example.com", PASSWORD);

    assertThat(sameUsername.getResponse().getStatus()).isEqualTo(400);
    assertThat(sameUsername.getResponse().getContentAsString())
        .contains("\"username\":[\"is already taken\"]")
        .doesNotContain("\"email\"");
    assertThat(sameEmail.getResponse().getStatus()).isEqualTo(400);
    assertThat(sameEmail.getResponse().getContentAsString())
        .contains("\"email\":[\"is already registered\"]")
        .doesNotContain("\"username\"");
    assertThat(both.getResponse().getContentAsString())
        .contains("\"username\":[\"is already taken\"]", "\"email\":[\"is already registered\"]");
    assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(1);
    assertThat(mail.sent()).as("no email for failed registrations").isEmpty();
  }

  @Test
  void registrationNeedsACsrfToken() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"username\":\"alice\",\"displayName\":\"A\",\"email\":\"a@example.com\","
                        + "\"password\":\"correct horse\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void registrationIsDocumentedInOpenApi() throws Exception {
    mockMvc
        .perform(get("/api/v3/api-docs"))
        .andExpect(jsonPath("$.paths['/api/auth/register']").exists())
        .andExpect(jsonPath("$.paths['/api/auth/verify-email']").exists())
        .andExpect(jsonPath("$.paths['/api/auth/resend-verification']").exists());
  }
}

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
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class EmailVerificationTests {

  @Autowired MockMvc mockMvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired RecordingMailSender mail;

  @BeforeEach
  void cleanUp() {
    jdbc.update("delete from spring_session");
    jdbc.update("delete from users");
    mail.reset();
  }

  ResultActions verify(String token) throws Exception {
    return mockMvc.perform(
        post("/api/auth/verify-email")
            .with(Csrf.token(mockMvc))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"" + token + "\"}"));
  }

  ResultActions resend(Cookie session) throws Exception {
    return mockMvc.perform(
        post("/api/auth/resend-verification").cookie(session).with(Csrf.token(mockMvc)));
  }

  @Test
  void theLinkVerifiesTheAddressEvenWithoutBeingLoggedIn() throws Exception {
    Cookie session = session(register(mockMvc, "alice", "alice@example.com", PASSWORD));
    String token = mail.last().verificationToken();

    verify(token).andExpect(status().isNoContent()); // no session cookie: "another device"

    assertThat(
            jdbc.queryForObject(
                "select email_verified_at is not null from users where username = 'alice'",
                Boolean.class))
        .isTrue();
    mockMvc
        .perform(get("/api/me").cookie(session))
        .andExpect(jsonPath("$.emailVerified").value(true));
  }

  @Test
  void aLinkWorksOnlyOnce() throws Exception {
    register(mockMvc, "alice", "alice@example.com", PASSWORD);
    String token = mail.last().verificationToken();

    verify(token).andExpect(status().isNoContent());
    verify(token)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("This verification link is invalid or has expired."));
  }

  @Test
  void unknownAndMalformedTokensAreRejectedTheSameWay() throws Exception {
    for (String token : new String[] {"nope", SecureTokens.generate(), "x".repeat(43)}) {
      verify(token)
          .andExpect(status().isBadRequest())
          .andExpect(
              jsonPath("$.detail").value("This verification link is invalid or has expired."));
    }
    mockMvc
        .perform(
            post("/api/auth/verify-email")
                .with(Csrf.token(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.token").isNotEmpty());
  }

  @Test
  void anExpiredLinkIsRejected() throws Exception {
    register(mockMvc, "alice", "alice@example.com", PASSWORD);
    String token = mail.last().verificationToken();
    jdbc.update("update email_verification_tokens set expires_at = now() - interval '1 second'");

    verify(token).andExpect(status().isBadRequest());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from users where email_verified_at is not null", Integer.class))
        .isZero();
  }

  @Test
  void aLinkLastsTwentyFourHours() throws Exception {
    register(mockMvc, "alice", "alice@example.com", PASSWORD);

    Boolean about24Hours =
        jdbc.queryForObject(
            "select expires_at between now() + interval '23 hours 59 minutes'"
                + " and now() + interval '24 hours 1 minute' from email_verification_tokens",
            Boolean.class);
    assertThat(about24Hours).isTrue();
  }

  @Test
  void aLinkIsVoidIfTheAddressChangedSinceItWasSent() throws Exception {
    register(mockMvc, "alice", "alice@example.com", PASSWORD);
    String token = mail.last().verificationToken();
    jdbc.update("update users set email = 'new@example.com'");

    verify(token).andExpect(status().isBadRequest());
  }

  @Test
  void resendSendsANewLinkAndVoidsTheOldOne() throws Exception {
    Cookie session = session(register(mockMvc, "alice", "alice@example.com", PASSWORD));
    String first = mail.last().verificationToken();

    resend(session).andExpect(status().isNoContent());

    assertThat(mail.sent()).hasSize(2);
    String second = mail.last().verificationToken();
    assertThat(second).isNotEqualTo(first);
    assertThat(jdbc.queryForObject("select count(*) from email_verification_tokens", Integer.class))
        .isEqualTo(1);
    verify(first).andExpect(status().isBadRequest());
    verify(second).andExpect(status().isNoContent());
  }

  @Test
  void resendDoesNothingOnceVerified() throws Exception {
    Cookie session = session(register(mockMvc, "alice", "alice@example.com", PASSWORD));
    verify(mail.last().verificationToken()).andExpect(status().isNoContent());
    mail.reset();

    resend(session).andExpect(status().isNoContent());

    assertThat(mail.sent()).isEmpty();
  }

  @Test
  void resendNeedsALogin() throws Exception {
    mockMvc
        .perform(post("/api/auth/resend-verification").with(Csrf.token(mockMvc)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void resendWorksAfterTheMailServerRecovers() throws Exception {
    mail.setFailing(true);
    MvcResult registered = register(mockMvc, "alice", "alice@example.com", PASSWORD);
    assertThat(mail.sent()).isEmpty();
    mail.setFailing(false);

    resend(session(registered)).andExpect(status().isNoContent());

    assertThat(mail.sent()).hasSize(1);
    verify(mail.last().verificationToken()).andExpect(status().isNoContent());
  }

  @Test
  void deletingAUserDeletesTheirTokens() throws Exception {
    register(mockMvc, "alice", "alice@example.com", PASSWORD);

    jdbc.update("delete from users");

    assertThat(jdbc.queryForObject("select count(*) from email_verification_tokens", Integer.class))
        .isZero();
  }
}

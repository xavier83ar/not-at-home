package com.notathome.auth;

import static com.notathome.support.Accounts.PASSWORD;
import static com.notathome.support.Accounts.register;
import static com.notathome.support.Accounts.session;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.notathome.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class RequiresVerifiedEmailTests {

  @Autowired MockMvc mockMvc;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void cleanUp() {
    jdbc.update("delete from spring_session");
    jdbc.update("delete from users");
  }

  @Test
  void unverifiedUsersGetA403TheFrontendCanRecognise() throws Exception {
    Cookie session = session(register(mockMvc, "alice", "alice@example.com", PASSWORD));

    mockMvc
        .perform(get("/api/test/verified-only").cookie(session))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"))
        .andExpect(jsonPath("$.detail").value("Verify your email address to do this."));
  }

  @Test
  void endpointsWithoutTheAnnotationStayOpenToUnverifiedUsers() throws Exception {
    Cookie session = session(register(mockMvc, "alice", "alice@example.com", PASSWORD));

    mockMvc.perform(get("/api/test/open-to-all-users").cookie(session)).andExpect(status().isOk());
  }

  @Test
  void verifyingTakesEffectInTheSessionThatWasAlreadyOpen() throws Exception {
    Cookie session = session(register(mockMvc, "alice", "alice@example.com", PASSWORD));
    mockMvc
        .perform(get("/api/test/verified-only").cookie(session))
        .andExpect(status().isForbidden());

    // Verified from another device: the open session has to notice.
    jdbc.update("update users set email_verified_at = now()");

    mockMvc
        .perform(get("/api/test/verified-only").cookie(session))
        .andExpect(status().isOk())
        .andExpect(content().string("ok"));
  }

  @Test
  void guestsStillGetA401() throws Exception {
    mockMvc.perform(get("/api/test/verified-only")).andExpect(status().isUnauthorized());
  }
}

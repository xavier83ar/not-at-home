package com.notathome.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Registration and login the way the frontend does them, for tests that need an account. */
public final class Accounts {

  public static final String PASSWORD = "correct horse";

  private Accounts() {}

  public static MvcResult register(MockMvc mockMvc, String username, String email, String password)
      throws Exception {
    return register(mockMvc, username, username, email, password);
  }

  public static MvcResult register(
      MockMvc mockMvc, String username, String displayName, String email, String password)
      throws Exception {
    String json =
        "{\"username\":\"%s\",\"displayName\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}"
            .formatted(username, displayName, email, password);
    return mockMvc
        .perform(
            post("/api/auth/register")
                .with(Csrf.token(mockMvc))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andReturn();
  }

  /** Like a browser: when a response sets the same cookie twice, the last one wins. */
  public static Cookie lastCookie(MvcResult result, String name) {
    Cookie found = null;
    for (Cookie cookie : result.getResponse().getCookies()) {
      if (cookie.getName().equals(name)) {
        found = cookie;
      }
    }
    return found;
  }

  public static Cookie session(MvcResult result) {
    return lastCookie(result, "SESSION");
  }
}

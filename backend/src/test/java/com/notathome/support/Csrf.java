package com.notathome.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Sends the CSRF token the way the browser does: fetch the cookie from {@code /api/csrf}, then send
 * it back as a cookie and in the {@code X-XSRF-TOKEN} header.
 *
 * <p>Use this instead of spring-security-test's {@code csrf()}. That one swaps the repository
 * inside the shared {@code CsrfFilter} for good, which breaks every cookie-based request that runs
 * after it in the same cached application context.
 */
public final class Csrf {

  private Csrf() {}

  public static RequestPostProcessor token(MockMvc mockMvc) throws Exception {
    Cookie xsrf =
        mockMvc.perform(get("/api/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    return request -> {
      List<Cookie> cookies = new ArrayList<>();
      if (request.getCookies() != null) {
        cookies.addAll(List.of(request.getCookies()));
      }
      cookies.add(xsrf);
      request.setCookies(cookies.toArray(Cookie[]::new));
      request.addHeader("X-XSRF-TOKEN", xsrf.getValue());
      return request;
    };
  }
}

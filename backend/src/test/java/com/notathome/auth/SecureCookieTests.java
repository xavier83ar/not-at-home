package com.notathome.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.notathome.config.SecurityConfigAccess;
import com.notathome.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;

/**
 * In production the cookies must be {@code Secure} even when the request that sets them doesn't
 * look secure: server-side renders reach the backend over plain HTTP, and that is also where the
 * remember-me cookie is re-issued after a browser restart. Dev runs on HTTP, so only these tests
 * can see it.
 */
@IntegrationTest
class SecureCookieTests {

  @Autowired RememberMeTokenRepository tokenRepository;

  /** A plain-HTTP request, like the one a server-side render makes. */
  private final MockHttpServletRequest httpRequest = new MockHttpServletRequest();

  AuthRememberMeServices rememberMe(boolean secureCookies) {
    return new RememberMeConfiguration()
        .rememberMeServices(
            username -> {
              throw new UsernameNotFoundException(username);
            },
            tokenRepository,
            secureCookies);
  }

  Cookie rememberMeCookieAfterLogin(AuthRememberMeServices services) {
    MockHttpServletResponse response = new MockHttpServletResponse();
    services.loginSuccess(
        httpRequest,
        response,
        UsernamePasswordAuthenticationToken.authenticated("alice", null, java.util.List.of()));
    return response.getCookie("remember-me");
  }

  @Test
  void rememberMeCookieIsSecureWhenProductionSettingsSayAreSecureEvenOverPlainHttp() {
    Cookie cookie = rememberMeCookieAfterLogin(rememberMe(true));

    assertThat(cookie.getSecure()).isTrue();
    assertThat(cookie.isHttpOnly()).isTrue();
  }

  @Test
  void rememberMeCookieFollowsTheRequestInDevSoPlainHttpStillWorks() {
    assertThat(rememberMeCookieAfterLogin(rememberMe(false)).getSecure()).isFalse();

    httpRequest.setSecure(true);
    assertThat(rememberMeCookieAfterLogin(rememberMe(false)).getSecure()).isTrue();
  }

  @Test
  void csrfCookieIsSecureWhenProductionSettingsSayAreSecureEvenOverPlainHttp() {
    assertThat(csrfCookie(true).getSecure()).isTrue();
    assertThat(csrfCookie(false).getSecure()).isFalse();
  }

  private Cookie csrfCookie(boolean secureCookies) {
    CsrfTokenRepository repository = SecurityConfigAccess.csrfTokenRepository(secureCookies);
    MockHttpServletResponse response = new MockHttpServletResponse();
    CsrfToken token = new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "abc");
    repository.saveToken(token, httpRequest, response);
    return response.getCookie("XSRF-TOKEN");
  }
}

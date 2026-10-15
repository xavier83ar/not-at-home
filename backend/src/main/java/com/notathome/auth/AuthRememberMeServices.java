package com.notathome.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.CookieTheftException;
import org.springframework.security.web.authentication.rememberme.InvalidCookieException;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationException;

/**
 * "Remember me" (FR-1.7): a cookie that brings the session back for 30 days after the browser was
 * closed. The cookie holds a random series and token that change on every use; the database keeps
 * the current pair, so a stolen cookie stops working once the real owner comes back.
 *
 * <p>Differences from Spring's defaults:
 *
 * <ul>
 *   <li>It is requested by the JSON login body, so every login that reaches {@code loginSuccess} is
 *       remembered ({@code alwaysRemember}).
 *   <li><b>Parallel requests are not theft.</b> When a browser restarts and restores several tabs,
 *       each sends the same cookie at once. Spring would accept the first and treat the others as a
 *       replayed token, revoking all of the user's remembered logins (measured: 5 tabs lost the
 *       login in about half the restarts). Here the token is replaced with an atomic
 *       compare-and-set, and the previous token stays valid for {@value
 *       RememberMeTokenRepository#GRACE_SECONDS} seconds: such a request gets its session but no
 *       new cookie (the winner's response already carries it), so a replay in that window yields
 *       one session, never a lasting credential. A token any older is treated as stolen.
 *   <li>A reused old token revokes the user's remember-me logins, as Spring does, but the request
 *       carries on as a guest instead of failing with an error.
 *   <li>Logging out removes all the user's remember-me tokens, so it works on every device.
 * </ul>
 */
class AuthRememberMeServices extends PersistentTokenBasedRememberMeServices {

  static final String COOKIE_NAME = SPRING_SECURITY_REMEMBER_ME_COOKIE_KEY;

  static final int VALIDITY_SECONDS = 30 * 24 * 60 * 60;

  private static final Logger log = LoggerFactory.getLogger(AuthRememberMeServices.class);

  private final RememberMeTokenRepository tokens;

  AuthRememberMeServices(
      UserDetailsService userDetailsService, RememberMeTokenRepository tokenRepository) {
    // The key only ties this service to its authentication provider for the length of one
    // request, so a new one per start is fine.
    super(UUID.randomUUID().toString(), userDetailsService, tokenRepository);
    this.tokens = tokenRepository;
    setAlwaysRemember(true);
    setTokenValiditySeconds(VALIDITY_SECONDS);
    setCookieCustomizer(cookie -> cookie.setAttribute("SameSite", "Lax"));
  }

  @Override
  public Authentication autoLogin(HttpServletRequest request, HttpServletResponse response) {
    try {
      return super.autoLogin(request, response);
    } catch (CookieTheftException e) {
      // The cookie is already cleared and the user's tokens removed.
      log.warn("An old remember-me token was presented again; remember-me logins were revoked");
      return null;
    }
  }

  @Override
  protected UserDetails processAutoLoginCookie(
      String[] cookieTokens, HttpServletRequest request, HttpServletResponse response) {
    if (cookieTokens.length != 2) {
      throw new InvalidCookieException(
          "Cookie token did not contain 2 tokens, but " + cookieTokens.length);
    }
    String series = cookieTokens[0];
    String presented = cookieTokens[1];

    RememberMeTokenRepository.Stored stored = load(series);
    if (stored.lastUsed().plusSeconds(getTokenValiditySeconds()).isBefore(Instant.now())) {
      throw new RememberMeAuthenticationException("Remember-me login has expired");
    }

    if (presented.equals(stored.token())) {
      String next = generateTokenData();
      if (tokens.rotate(series, presented, next)) {
        setCookie(new String[] {series, next}, getTokenValiditySeconds(), request, response);
        return getUserDetailsService().loadUserByUsername(stored.username());
      }
      // Another request replaced the token between our read and our update.
      stored = load(series);
    }

    if (presented.equals(stored.previousToken()) && stored.previousTokenInGrace()) {
      // A parallel request from the same browser, whose response will carry the new cookie.
      return getUserDetailsService().loadUserByUsername(stored.username());
    }

    tokens.removeUserTokens(stored.username());
    throw new CookieTheftException(
        "Invalid remember-me token (series/token mismatch). Implies previous cookie theft attack.");
  }

  private RememberMeTokenRepository.Stored load(String series) {
    return tokens
        .find(series)
        .orElseThrow(
            () ->
                new RememberMeAuthenticationException(
                    "No persistent token found for series id: " + series));
  }
}

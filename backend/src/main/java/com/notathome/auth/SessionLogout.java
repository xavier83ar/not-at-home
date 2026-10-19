package com.notathome.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.CompositeLogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

/**
 * Ends the current request's session: forgets its remember-me login, drops the CSRF token and
 * invalidates the session. Used by logout and by account deletion.
 */
@Component
class SessionLogout {

  private final LogoutHandler logoutHandler;

  SessionLogout(
      AuthRememberMeServices rememberMeServices, CsrfTokenRepository csrfTokenRepository) {
    // Remember-me first: it needs the authentication to know whose tokens to remove.
    this.logoutHandler =
        new CompositeLogoutHandler(
            rememberMeServices,
            new CsrfLogoutHandler(csrfTokenRepository),
            new SecurityContextLogoutHandler());
  }

  void logout(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
    logoutHandler.logout(request, response, authentication);
  }
}

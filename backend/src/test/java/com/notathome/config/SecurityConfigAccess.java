package com.notathome.config;

import org.springframework.security.web.csrf.CsrfTokenRepository;

/** Lets tests outside this package build the (package-private) security beans directly. */
public final class SecurityConfigAccess {

  private SecurityConfigAccess() {}

  public static CsrfTokenRepository csrfTokenRepository(boolean secureCookies) {
    return new SecurityConfig().csrfTokenRepository(secureCookies);
  }
}

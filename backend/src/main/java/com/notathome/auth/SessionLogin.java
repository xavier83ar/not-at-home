package com.notathome.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.stereotype.Component;

/**
 * Starts a logged-in session for an already authenticated user. Spring Security does not do this
 * for custom login endpoints, so it runs the steps a login filter would: fixation protection, CSRF
 * token rotation, and saving the security context into the session. Used by login and registration.
 */
@Component
class SessionLogin {

  private final SessionAuthenticationStrategy sessionStrategy;
  private final SecurityContextRepository securityContextRepository =
      new HttpSessionSecurityContextRepository();
  private final SecurityContextHolderStrategy securityContextHolderStrategy =
      SecurityContextHolder.getContextHolderStrategy();

  SessionLogin(
      CsrfTokenRepository csrfTokenRepository, CsrfTokenRequestHandler csrfTokenRequestHandler) {
    CsrfAuthenticationStrategy csrfStrategy = new CsrfAuthenticationStrategy(csrfTokenRepository);
    csrfStrategy.setRequestHandler(csrfTokenRequestHandler);
    this.sessionStrategy =
        new CompositeSessionAuthenticationStrategy(
            List.of(new ChangeSessionIdAuthenticationStrategy(), csrfStrategy));
  }

  void start(
      Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
    // Order matters: fixation protection first, so the context is saved into the new session.
    sessionStrategy.onAuthentication(authentication, request, response);
    SecurityContext context = securityContextHolderStrategy.createEmptyContext();
    context.setAuthentication(authentication);
    securityContextHolderStrategy.setContext(context);
    securityContextRepository.saveContext(context, request, response);

    // The CSRF strategy discarded the old token; load the replacement so its cookie is sent now.
    ((CsrfToken) request.getAttribute(CsrfToken.class.getName())).getToken();
  }
}

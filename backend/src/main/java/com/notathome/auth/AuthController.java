package com.notathome.auth;

import com.notathome.users.User;
import com.notathome.users.UserRepository;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.CompositeLogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * JSON login and logout. Spring Security does not do the session work for a custom login endpoint,
 * so this controller runs the steps a login filter would: fixation protection, CSRF token rotation,
 * and saving the security context into the session.
 */
@RestController
class AuthController {

  private final AuthenticationManager authenticationManager;
  private final UserRepository users;
  private final SessionAuthenticationStrategy sessionStrategy;
  private final LogoutHandler logoutHandler;
  private final SecurityContextRepository securityContextRepository =
      new HttpSessionSecurityContextRepository();
  private final SecurityContextHolderStrategy securityContextHolderStrategy =
      SecurityContextHolder.getContextHolderStrategy();

  AuthController(
      AuthenticationManager authenticationManager,
      UserRepository users,
      CsrfTokenRepository csrfTokenRepository,
      CsrfTokenRequestHandler csrfTokenRequestHandler) {
    this.authenticationManager = authenticationManager;
    this.users = users;

    CsrfAuthenticationStrategy csrfStrategy = new CsrfAuthenticationStrategy(csrfTokenRepository);
    csrfStrategy.setRequestHandler(csrfTokenRequestHandler);
    this.sessionStrategy =
        new CompositeSessionAuthenticationStrategy(
            List.of(new ChangeSessionIdAuthenticationStrategy(), csrfStrategy));
    this.logoutHandler =
        new CompositeLogoutHandler(
            new CsrfLogoutHandler(csrfTokenRepository), new SecurityContextLogoutHandler());
  }

  record LoginRequest(
      @NotBlank @Size(max = 254) String login, @NotBlank @Size(max = 72) String password) {}

  /**
   * Issues the CSRF cookie. The token is created lazily, so the frontend calls this before its
   * first state-changing request and again after logout, which clears the cookie.
   */
  @GetMapping("/api/csrf")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void csrf(@Parameter(hidden = true) CsrfToken token) {
    token.getToken();
  }

  /** Logs in with username or email plus password. */
  @PostMapping("/api/auth/login")
  CurrentUserResponse login(
      @Valid @RequestBody LoginRequest body,
      HttpServletRequest request,
      HttpServletResponse response) {
    Authentication authentication;
    try {
      authentication =
          authenticationManager.authenticate(
              UsernamePasswordAuthenticationToken.unauthenticated(
                  body.login().strip(), body.password()));
    } catch (AuthenticationException e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    // Order matters: fixation protection first, so the context is saved into the new session.
    sessionStrategy.onAuthentication(authentication, request, response);
    SecurityContext context = securityContextHolderStrategy.createEmptyContext();
    context.setAuthentication(authentication);
    securityContextHolderStrategy.setContext(context);
    securityContextRepository.saveContext(context, request, response);

    // The CSRF strategy discarded the old token; load the replacement so its cookie is sent now.
    ((CsrfToken) request.getAttribute(CsrfToken.class.getName())).getToken();

    AuthUser principal = (AuthUser) authentication.getPrincipal();
    User user = users.findByIdAndDeletedAtIsNull(principal.getId()).orElseThrow();
    return CurrentUserResponse.from(user);
  }

  /** Ends the session. Safe to call when not logged in. */
  @PostMapping("/api/auth/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void logout(
      Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
    logoutHandler.logout(request, response, authentication);
  }
}

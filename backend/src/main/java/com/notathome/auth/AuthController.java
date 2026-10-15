package com.notathome.auth;

import com.notathome.users.User;
import com.notathome.users.UserRepository;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.logout.CompositeLogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** JSON login and logout. */
@RestController
class AuthController {

  private final AuthenticationManager authenticationManager;
  private final UserRepository users;
  private final SessionLogin sessionLogin;
  private final LogoutHandler logoutHandler;

  AuthController(
      AuthenticationManager authenticationManager,
      UserRepository users,
      SessionLogin sessionLogin,
      CsrfTokenRepository csrfTokenRepository) {
    this.authenticationManager = authenticationManager;
    this.users = users;
    this.sessionLogin = sessionLogin;
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

    sessionLogin.start(authentication, request, response);

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

package com.notathome.auth;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;

import com.notathome.ratelimit.RateLimitPolicy;
import com.notathome.ratelimit.RateLimited;
import com.notathome.users.User;
import com.notathome.users.UserRepository;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.WebUtils;

/** JSON login and logout. */
@RestController
class AuthController {

  private final AuthenticationManager authenticationManager;
  private final UserRepository users;
  private final SessionLogin sessionLogin;
  private final AuthRememberMeServices rememberMeServices;
  private final SessionLogout sessionLogout;
  private final EmailVerificationService verification;

  AuthController(
      AuthenticationManager authenticationManager,
      UserRepository users,
      SessionLogin sessionLogin,
      AuthRememberMeServices rememberMeServices,
      SessionLogout sessionLogout,
      EmailVerificationService verification) {
    this.authenticationManager = authenticationManager;
    this.users = users;
    this.sessionLogin = sessionLogin;
    this.rememberMeServices = rememberMeServices;
    this.sessionLogout = sessionLogout;
    this.verification = verification;
  }

  /**
   * {@code rememberMe} keeps the login for 30 days after the browser is closed (FR-1.7). Optional,
   * and a wrapper type on purpose: Jackson 3 refuses a missing value for a primitive boolean.
   */
  record LoginRequest(
      @NotBlank @Size(max = 254) String login,
      @NotBlank @Size(max = 72) String password,
      @Schema(requiredMode = NOT_REQUIRED) Boolean rememberMe) {}

  /**
   * Issues the CSRF cookie. The token is created lazily, so the frontend calls this before its
   * first state-changing request and again after logout, which clears the cookie.
   */
  @GetMapping("/api/csrf")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void csrf(@Parameter(hidden = true) CsrfToken token) {
    token.getToken();
  }

  /**
   * Logs in with username or email plus password. 401 for wrong credentials; 403 with code {@code
   * ACCOUNT_BANNED} for a banned account (FR-1.10).
   */
  @PostMapping("/api/auth/login")
  @RateLimited(RateLimitPolicy.LOGIN)
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
    } catch (LockedException e) {
      // Only thrown once the password was right (see SecurityConfig).
      throw new AccountBannedException();
    } catch (AuthenticationException e) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    sessionLogin.start(authentication, request, response);
    if (Boolean.TRUE.equals(body.rememberMe())) {
      rememberMeServices.loginSuccess(request, response, authentication);
    } else if (WebUtils.getCookie(request, AuthRememberMeServices.COOKIE_NAME) != null) {
      // The request carries someone else's remember-me cookie (it was just used to restore their
      // session, and the response would hand back a fresh one). Drop it, or closing the browser
      // would log this person out and the previous one back in.
      rememberMeServices.loginFail(request, response);
    }

    AuthUser principal = (AuthUser) authentication.getPrincipal();
    User user = users.findByIdAndDeletedAtIsNull(principal.getId()).orElseThrow();
    return CurrentUserResponse.from(user, verification.pendingEmail(user.getId()).orElse(null));
  }

  /** Ends the session. Safe to call when not logged in. */
  @PostMapping("/api/auth/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void logout(
      Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
    sessionLogout.logout(request, response, authentication);
  }
}

package com.notathome.auth;

import com.notathome.users.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class RegistrationController {

  private final RegistrationService registration;
  private final SessionLogin sessionLogin;

  RegistrationController(RegistrationService registration, SessionLogin sessionLogin) {
    this.registration = registration;
    this.sessionLogin = sessionLogin;
  }

  /**
   * Creates an account and logs it in; the user can look around while their email is unverified
   * (FR-1.9). Field problems come back as 400 with an {@code errors} map.
   */
  @PostMapping("/api/auth/register")
  @ResponseStatus(HttpStatus.CREATED)
  CurrentUserResponse register(
      @Valid @RequestBody RegisterRequest body,
      HttpServletRequest request,
      HttpServletResponse response) {
    User user = registration.register(body);

    AuthUser principal = new AuthUser(user);
    principal.eraseCredentials(); // the hash must not end up in the session table
    Authentication authentication =
        UsernamePasswordAuthenticationToken.authenticated(
            principal, null, principal.getAuthorities());
    sessionLogin.start(authentication, request, response);
    return CurrentUserResponse.from(user);
  }
}

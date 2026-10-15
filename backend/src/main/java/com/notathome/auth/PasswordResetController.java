package com.notathome.auth;

import com.notathome.common.MaxUtf8Bytes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PasswordResetController {

  private final PasswordResetService passwordReset;
  private final AuthRememberMeServices rememberMeServices;

  PasswordResetController(
      PasswordResetService passwordReset, AuthRememberMeServices rememberMeServices) {
    this.passwordReset = passwordReset;
    this.rememberMeServices = rememberMeServices;
  }

  record ForgotPasswordRequest(
      @NotBlank
          @Size(max = 254, message = "is too long")
          @Email(message = "must be a valid email address")
          String email) {}

  record ResetPasswordRequest(
      @NotBlank @Size(max = 100) String token,
      // Same rules as at registration: bcrypt only reads the first 72 bytes.
      @NotNull
          @Size(min = 8, message = "must be at least 8 characters")
          @MaxUtf8Bytes(value = 72, message = "must be at most 72 bytes (about 72 characters)")
          String password) {}

  /**
   * Asks for a password reset email. The answer is always the same 204, whether or not the address
   * belongs to an account, so this can't be used to find out who is registered (FR-1.8).
   */
  @PostMapping("/api/auth/forgot-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void forgotPassword(@Valid @RequestBody ForgotPasswordRequest body) {
    passwordReset.request(body.email());
  }

  /**
   * Sets a new password with the token from the email, and logs the user out everywhere, this
   * browser included. 400 with field errors for a bad password (the link stays usable); 400 with a
   * message for a bad link.
   */
  @PostMapping("/api/auth/reset-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void resetPassword(
      @Valid @RequestBody ResetPasswordRequest body,
      Authentication authentication,
      HttpServletRequest request,
      HttpServletResponse response) {
    passwordReset.reset(body.token().strip(), body.password());

    // The service ended every stored session, but this request may have one of its own that isn't
    // stored yet: a browser that only had a remember-me cookie got a session from it before this
    // method ran. Drop that one and the cookie too, so "logged out everywhere" is true here as
    // well. Only this browser's login is touched, not the tokens of whoever it belonged to.
    new SecurityContextLogoutHandler().logout(request, response, authentication);
    rememberMeServices.loginFail(request, response);
  }
}

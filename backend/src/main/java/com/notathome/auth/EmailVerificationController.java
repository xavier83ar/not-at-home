package com.notathome.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class EmailVerificationController {

  private final EmailVerificationService verification;

  EmailVerificationController(EmailVerificationService verification) {
    this.verification = verification;
  }

  record VerifyEmailRequest(@NotBlank @Size(max = 100) String token) {}

  /**
   * Confirms an email address with the token from the link. A POST, not a GET: mail scanners and
   * link previews open links, and must not be able to use them up. No login needed, since people
   * often open the link on another device.
   */
  @PostMapping("/api/auth/verify-email")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void verify(@Valid @RequestBody VerifyEmailRequest body) {
    verification.verify(body.token().strip());
  }

  /** Sends a new verification email to the logged-in user. */
  @PostMapping("/api/auth/resend-verification")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void resend(@AuthenticationPrincipal AuthUser principal) {
    verification.resend(principal.getId());
  }
}

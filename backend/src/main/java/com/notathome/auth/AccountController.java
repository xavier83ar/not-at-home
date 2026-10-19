package com.notathome.auth;

import com.notathome.common.MaxUtf8Bytes;
import com.notathome.ratelimit.RateLimitPolicy;
import com.notathome.ratelimit.RateLimited;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Password, email and deletion of one's own account (FR-8.3, FR-8.4). None of them needs a verified
 * email (section 5.4), and all of them ask for the current password.
 */
@RestController
class AccountController {

  private final AccountService accounts;
  private final SessionLogout sessionLogout;

  AccountController(AccountService accounts, SessionLogout sessionLogout) {
    this.accounts = accounts;
    this.sessionLogout = sessionLogout;
  }

  // The current password is only compared, so it has no rules of its own; the cap just bounds the
  // work of checking it.
  record ChangePasswordRequest(
      @NotBlank @Size(max = 100) String currentPassword,
      // Same rules as at registration.
      @NotNull
          @Size(min = 8, message = "must be at least 8 characters")
          @MaxUtf8Bytes(value = 72, message = "must be at most 72 bytes (about 72 characters)")
          String newPassword) {}

  record ChangeEmailRequest(
      @NotBlank @Size(max = 100) String currentPassword,
      @NotBlank
          @Size(max = 254, message = "is too long")
          @Email(message = "must be a valid email address")
          @Pattern(regexp = ".+@.+\\..+", message = "must be a valid email address")
          String email) {}

  record DeleteAccountRequest(@NotBlank @Size(max = 100) String password) {}

  /** FR-8.3. Other sessions end; the one that made the change stays. */
  @PutMapping("/api/me/password")
  @RateLimited(RateLimitPolicy.CHANGE_PASSWORD)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void changePassword(
      @Valid @RequestBody ChangePasswordRequest body,
      @AuthenticationPrincipal AuthUser principal,
      HttpServletRequest request) {
    accounts.changePassword(
        principal.getId(),
        body.currentPassword(),
        body.newPassword(),
        request.getSession().getId());
  }

  /**
   * FR-8.3. Only asks for the change: the address is replaced when the link sent to the new one is
   * used (see {@code POST /api/auth/verify-email}), and {@code GET /api/me} reports it as {@code
   * pendingEmail} until then.
   */
  @PostMapping("/api/me/email-change")
  @RateLimited(RateLimitPolicy.CHANGE_EMAIL)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void changeEmail(
      @Valid @RequestBody ChangeEmailRequest body, @AuthenticationPrincipal AuthUser principal) {
    accounts.requestEmailChange(principal.getId(), body.currentPassword(), body.email());
  }

  /**
   * FR-8.4. A POST rather than a DELETE with a body, which proxies and clients treat unevenly. The
   * response also logs out this browser (sessions, remember-me cookie, CSRF token).
   */
  @PostMapping("/api/me/delete-account")
  @RateLimited(RateLimitPolicy.DELETE_ACCOUNT)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteAccount(
      @Valid @RequestBody DeleteAccountRequest body,
      @AuthenticationPrincipal AuthUser principal,
      Authentication authentication,
      HttpServletRequest request,
      HttpServletResponse response) {
    accounts.deleteAccount(principal.getId(), body.password());
    sessionLogout.logout(request, response, authentication);
  }
}

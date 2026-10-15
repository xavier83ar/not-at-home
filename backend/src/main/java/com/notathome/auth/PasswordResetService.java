package com.notathome.auth;

import com.notathome.users.User;
import com.notathome.users.UserRepository;
import com.notathome.users.UserStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class PasswordResetService {

  static final Duration TOKEN_LIFETIME = Duration.ofHours(1);

  private final PasswordResetTokenRepository tokens;
  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final UserSessions userSessions;
  private final ApplicationEventPublisher events;

  PasswordResetService(
      PasswordResetTokenRepository tokens,
      UserRepository users,
      PasswordEncoder passwordEncoder,
      UserSessions userSessions,
      ApplicationEventPublisher events) {
    this.tokens = tokens;
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.userSessions = userSessions;
    this.events = events;
  }

  /**
   * Asks for a reset email for an address. Nothing happens on the caller's thread except handing
   * the request over, so the call takes the same time whether or not the address has an account
   * (FR-1.8); see {@link PasswordResetEmailSender} for the rest.
   */
  public void request(String email) {
    events.publishEvent(new PasswordResetRequested(email));
  }

  /** A reset link that is ready to be emailed. */
  record ResetLink(Long userId, String email, String displayName, String token) {}

  /**
   * Creates a reset token if the address belongs to an active account, and does nothing otherwise.
   * Banned users can't log in, so a reset would give them nothing.
   */
  @Transactional
  public Optional<ResetLink> createToken(String email) {
    return users
        .findByEmailIgnoreCaseAndDeletedAtIsNull(email.strip())
        .filter(user -> user.getStatus() == UserStatus.ACTIVE)
        .map(this::issueToken);
  }

  private ResetLink issueToken(User user) {
    tokens.deleteByUserIdAndUsedAtIsNull(user.getId()); // only the newest link works
    String token = SecureTokens.generate();
    tokens.save(
        new PasswordResetToken(
            user.getId(), SecureTokens.hash(token), Instant.now().plus(TOKEN_LIFETIME)));
    return new ResetLink(user.getId(), user.getEmail(), user.getDisplayName(), token);
  }

  /**
   * Sets a new password with a token from an email. Also works for accounts that have no password
   * yet (signed up with Google). Everyone logged in as this user is logged out, on every device and
   * including remembered logins: whoever knew the old password may be one of them.
   */
  @Transactional
  public void reset(String token, String newPassword) {
    Instant now = Instant.now();
    PasswordResetToken stored =
        tokens
            .findByTokenHash(SecureTokens.hash(token))
            .filter(candidate -> candidate.isUsable(now))
            .orElseThrow(PasswordResetService::invalidLink);
    // Atomic: of two requests with the same link, only one gets past this line.
    if (tokens.markUsed(stored.getId(), now) != 1) {
      throw invalidLink();
    }
    User user =
        users
            .findByIdAndDeletedAtIsNull(stored.getUserId())
            .orElseThrow(PasswordResetService::invalidLink);

    user.changePasswordHash(passwordEncoder.encode(newPassword));
    tokens.deleteByUserIdAndUsedAtIsNull(user.getId()); // no other link may still work
    userSessions.endAll(user.getUsername());
  }

  private static ResponseStatusException invalidLink() {
    return new ResponseStatusException(
        HttpStatus.BAD_REQUEST, "This password reset link is invalid or has expired.");
  }
}

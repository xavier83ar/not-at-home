package com.notathome.auth;

import com.notathome.users.User;
import com.notathome.users.UserRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class EmailVerificationService {

  static final Duration TOKEN_LIFETIME = Duration.ofHours(24);

  private final EmailVerificationTokenRepository tokens;
  private final UserRepository users;
  private final ApplicationEventPublisher events;

  EmailVerificationService(
      EmailVerificationTokenRepository tokens,
      UserRepository users,
      ApplicationEventPublisher events) {
    this.tokens = tokens;
    this.users = users;
    this.events = events;
  }

  /** Creates a fresh token, voiding any earlier unused ones, and queues the email. */
  @Transactional
  public void sendVerificationEmail(User user) {
    tokens.deleteByUserIdAndUsedAtIsNull(user.getId());
    String token = SecureTokens.generate();
    tokens.save(
        new EmailVerificationToken(
            user.getId(),
            user.getEmail(),
            SecureTokens.hash(token),
            Instant.now().plus(TOKEN_LIFETIME)));
    events.publishEvent(
        new VerificationEmailRequested(
            user.getId(), user.getEmail(), user.getDisplayName(), token));
  }

  /** Does nothing for an already verified account, so the call is safe to repeat. */
  @Transactional
  public void resend(Long userId) {
    User user =
        users
            .findByIdAndDeletedAtIsNull(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    if (!user.isEmailVerified()) {
      sendVerificationEmail(user);
    }
  }

  /**
   * Marks the address verified. The same answer for unknown, used and expired tokens, so a link
   * says nothing about why it failed beyond "get a new one".
   */
  @Transactional
  public void verify(String token) {
    Instant now = Instant.now();
    EmailVerificationToken stored =
        tokens
            .findByTokenHash(SecureTokens.hash(token))
            .filter(candidate -> candidate.isUsable(now))
            .orElseThrow(EmailVerificationService::invalidLink);
    User user =
        users
            .findByIdAndDeletedAtIsNull(stored.getUserId())
            // The address may have changed since the link was sent.
            .filter(candidate -> candidate.getEmail().equalsIgnoreCase(stored.getEmail()))
            .orElseThrow(EmailVerificationService::invalidLink);
    stored.markUsed(now);
    if (!user.isEmailVerified()) {
      user.markEmailVerified(now);
    }
  }

  private static ResponseStatusException invalidLink() {
    return new ResponseStatusException(
        HttpStatus.BAD_REQUEST, "This verification link is invalid or has expired.");
  }
}

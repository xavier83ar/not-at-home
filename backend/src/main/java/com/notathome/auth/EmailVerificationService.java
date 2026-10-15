package com.notathome.auth;

import com.notathome.ratelimit.RateLimitPolicy;
import com.notathome.ratelimit.RateLimiter;
import com.notathome.users.User;
import com.notathome.users.UserRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
  private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

  private final ApplicationEventPublisher events;
  private final RateLimiter rateLimiter;

  EmailVerificationService(
      EmailVerificationTokenRepository tokens,
      UserRepository users,
      ApplicationEventPublisher events,
      RateLimiter rateLimiter) {
    this.tokens = tokens;
    this.users = users;
    this.events = events;
    this.rateLimiter = rateLimiter;
  }

  /**
   * The email sent at registration. Over the per-address limit it is skipped without a word: the
   * person has just registered, and can ask again later.
   */
  @Transactional
  public void sendVerificationEmail(User user) {
    if (!rateLimiter.tryConsume(
        RateLimitPolicy.VERIFICATION_EMAIL_ADDRESS, RateLimiter.emailKey(user.getEmail()))) {
      log.warn("Verification email suppressed for user {}: too many for one address", user.getId());
      return;
    }
    issueToken(user);
  }

  /** Creates a fresh token, voiding any earlier unused ones, and queues the email. */
  private void issueToken(User user) {
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
      // Shares one budget per address with the registration email; here the user is told.
      rateLimiter.consume(
          RateLimitPolicy.VERIFICATION_EMAIL_ADDRESS, RateLimiter.emailKey(user.getEmail()));
      issueToken(user);
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

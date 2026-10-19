package com.notathome.auth;

import com.notathome.ratelimit.RateLimitPolicy;
import com.notathome.ratelimit.RateLimiter;
import com.notathome.users.User;
import com.notathome.users.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
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

  /**
   * FR-8.3: sends a link to the new address. The account keeps its current address until the link
   * is used. Shares the per-address budget with the other verification emails, so changing an
   * account's address can't be used to flood somebody else's inbox; here the user is told.
   */
  @Transactional
  public void requestEmailChange(User user, String newEmail) {
    rateLimiter.consume(RateLimitPolicy.VERIFICATION_EMAIL_ADDRESS, RateLimiter.emailKey(newEmail));
    issueToken(user, newEmail, true);
  }

  /** The address the user asked to change to and hasn't confirmed yet, if any. */
  @Transactional(readOnly = true)
  public Optional<String> pendingEmail(Long userId) {
    return tokens
        .findFirstByUserIdAndEmailChangeTrueAndUsedAtIsNullAndExpiresAtAfterOrderByIdDesc(
            userId, Instant.now())
        .map(EmailVerificationToken::getEmail);
  }

  private void issueToken(User user) {
    issueToken(user, user.getEmail(), false);
  }

  /** Creates a fresh token, voiding any earlier unused ones, and queues the email. */
  private void issueToken(User user, String email, boolean addressChange) {
    tokens.deleteByUserIdAndUsedAtIsNull(user.getId());
    String token = SecureTokens.generate();
    tokens.save(
        new EmailVerificationToken(
            user.getId(),
            email,
            SecureTokens.hash(token),
            Instant.now().plus(TOKEN_LIFETIME),
            addressChange));
    events.publishEvent(
        new VerificationEmailRequested(
            user.getId(), email, user.getDisplayName(), token, addressChange));
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
            .orElseThrow(EmailVerificationService::invalidLink);
    if (stored.isEmailChange() && !user.getEmail().equalsIgnoreCase(stored.getEmail())) {
      applyEmailChange(user, stored, now);
      return;
    }
    // The address may have changed since the link was sent.
    if (!user.getEmail().equalsIgnoreCase(stored.getEmail())) {
      throw invalidLink();
    }
    stored.markUsed(now);
    if (!user.isEmailVerified()) {
      user.markEmailVerified(now);
    }
  }

  /**
   * FR-8.3: the new address is now proven to be the user's, so it replaces the old one and counts
   * as verified. Someone else may have registered it since the link was sent; the unique index has
   * the last word on that.
   */
  private void applyEmailChange(User user, EmailVerificationToken stored, Instant now) {
    if (users.existsByEmailIgnoreCase(stored.getEmail())) {
      throw addressTaken();
    }
    stored.markUsed(now);
    user.changeEmail(stored.getEmail(), now);
    tokens.deleteByUserIdAndUsedAtIsNull(user.getId()); // no other link may still work
    try {
      users.saveAndFlush(user);
    } catch (DataIntegrityViolationException e) {
      throw addressTaken();
    }
  }

  private static ResponseStatusException addressTaken() {
    return new ResponseStatusException(
        HttpStatus.BAD_REQUEST, "That address now belongs to another account.");
  }

  private static ResponseStatusException invalidLink() {
    return new ResponseStatusException(
        HttpStatus.BAD_REQUEST, "This verification link is invalid or has expired.");
  }
}

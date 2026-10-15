package com.notathome.auth;

import com.notathome.config.AppProperties;
import com.notathome.mail.EmailTemplates;
import com.notathome.mail.MailService;
import com.notathome.ratelimit.RateLimitPolicy;
import com.notathome.ratelimit.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Does everything a reset request needs, on another thread: finds the account, creates the token
 * and sends the email. The request for a reset answers the same way, and in the same time, whether
 * or not the address has an account, so it can't be used to find out who is registered. Sending an
 * email takes much longer than doing nothing, and even the lookup and the insert take a measurable
 * moment, so none of it may happen before the response.
 */
@Component
class PasswordResetEmailSender {

  private static final Logger log = LoggerFactory.getLogger(PasswordResetEmailSender.class);

  private final PasswordResetService passwordReset;
  private final MailService mailService;
  private final AppProperties properties;
  private final RateLimiter rateLimiter;

  PasswordResetEmailSender(
      PasswordResetService passwordReset,
      MailService mailService,
      AppProperties properties,
      RateLimiter rateLimiter) {
    this.passwordReset = passwordReset;
    this.mailService = mailService;
    this.properties = properties;
    this.rateLimiter = rateLimiter;
  }

  @Async
  @EventListener
  void handle(PasswordResetRequested event) {
    // Per address, and before looking the address up: it counts for unknown addresses too, so what
    // happens (and how long it takes) is the same for every address. Over the limit nothing is
    // sent, and the request that asked is not told: it got its answer long ago.
    if (!rateLimiter.tryConsume(
        RateLimitPolicy.PASSWORD_RESET_ADDRESS, RateLimiter.emailKey(event.email()))) {
      log.warn("A password reset request was dropped: too many for one address");
      return;
    }
    try {
      // The token is committed before the email goes out: the service call is its own transaction.
      passwordReset.createToken(event.email()).ifPresent(this::send);
    } catch (RuntimeException e) {
      // No address in the log: it is personal data, and the person may not even have an account.
      log.error("Could not process a password reset request", e);
    }
  }

  private void send(PasswordResetService.ResetLink link) {
    String url = properties.frontendUrl() + "/reset-password?token=" + link.token();
    mailService.send(link.email(), EmailTemplates.resetPassword(link.displayName(), url));
  }
}

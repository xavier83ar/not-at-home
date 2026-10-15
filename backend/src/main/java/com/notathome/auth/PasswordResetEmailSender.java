package com.notathome.auth;

import com.notathome.config.AppProperties;
import com.notathome.mail.EmailTemplates;
import com.notathome.mail.MailService;
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

  PasswordResetEmailSender(
      PasswordResetService passwordReset, MailService mailService, AppProperties properties) {
    this.passwordReset = passwordReset;
    this.mailService = mailService;
    this.properties = properties;
  }

  @Async
  @EventListener
  void handle(PasswordResetRequested event) {
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

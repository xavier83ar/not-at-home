package com.notathome.auth;

import com.notathome.config.AppProperties;
import com.notathome.mail.EmailTemplates;
import com.notathome.mail.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends the verification email once the token is safely saved, so a rolled-back registration never
 * emails anyone. A mail server outage must not break registration either: it is logged, and the
 * user can ask for a new email.
 */
@Component
class VerificationEmailSender {

  private static final Logger log = LoggerFactory.getLogger(VerificationEmailSender.class);

  private final MailService mailService;
  private final AppProperties properties;

  VerificationEmailSender(MailService mailService, AppProperties properties) {
    this.mailService = mailService;
    this.properties = properties;
  }

  @TransactionalEventListener(fallbackExecution = true)
  void send(VerificationEmailRequested event) {
    String url = properties.frontendUrl() + "/verify-email?token=" + event.token();
    try {
      mailService.send(
          event.email(),
          event.addressChange()
              ? EmailTemplates.changeEmail(event.displayName(), url)
              : EmailTemplates.verifyEmail(event.displayName(), url));
    } catch (RuntimeException e) {
      // The address is personal data; the user id is enough to find the account.
      log.error("Could not send the verification email to user {}", event.userId(), e);
    }
  }
}

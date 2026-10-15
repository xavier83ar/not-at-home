package com.notathome.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.notathome.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.core.env.Environment;

@IntegrationTest
class MailConfigurationTests {

  @Autowired Environment environment;

  /**
   * Emails go out on the request thread, and JavaMail waits forever by default. These timeouts keep
   * a dead mail server from hanging registration. Tests replace the sender, so this is the only
   * check that the settings are really picked up.
   */
  @Test
  void mailServerTimeoutsAreSet() {
    // Bound by hand: the recording sender makes Boot's mail auto-configuration back off.
    MailProperties mailProperties =
        Binder.get(environment).bind("spring.mail", MailProperties.class).get();

    assertThat(mailProperties.getProperties())
        .containsEntry("mail.smtp.connectiontimeout", "5000")
        .containsEntry("mail.smtp.timeout", "5000")
        .containsEntry("mail.smtp.writetimeout", "5000");
  }
}

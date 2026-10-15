package com.notathome.mail;

import com.notathome.config.AppProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/** Sends {@link EmailMessage}s as multipart emails (text and HTML) with the logo embedded. */
@Service
public class MailService {

  private static final String SENDER_NAME = "Not at Home";

  private final JavaMailSender mailSender;
  private final AppProperties properties;

  MailService(JavaMailSender mailSender, AppProperties properties) {
    this.mailSender = mailSender;
    this.properties = properties;
  }

  /**
   * @throws MailException if the message can't be built or the mail server refuses it
   */
  public void send(String to, EmailMessage message) {
    MimeMessage mime = mailSender.createMimeMessage();
    try {
      MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
      helper.setFrom(properties.mail().from(), SENDER_NAME);
      helper.setTo(to);
      helper.setSubject(message.subject());
      helper.setText(message.text(), message.html());
      // Mail clients don't load images from the web by default, so the logo travels inside.
      helper.addInline(EmailTemplates.LOGO_CID, new ClassPathResource("mail/logo.png"));
    } catch (MessagingException | UnsupportedEncodingException e) {
      throw new MailPreparationException("Could not build the email", e);
    }
    mailSender.send(mime);
  }
}

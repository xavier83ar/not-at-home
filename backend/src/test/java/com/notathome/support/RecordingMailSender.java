package com.notathome.support;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Keeps emails in memory instead of talking to an SMTP server. */
public class RecordingMailSender extends JavaMailSenderImpl {

  /** One sent email, with its bodies already extracted. */
  public record Sent(String from, String to, String subject, String text, String html) {

    private static final Pattern TOKEN_LINK = Pattern.compile("/verify-email\\?token=([\\w-]+)");

    /** The token in the first verification link of the plain-text body. */
    public String verificationToken() {
      Matcher matcher = TOKEN_LINK.matcher(text);
      if (!matcher.find()) {
        throw new AssertionError("No verification link in: " + text);
      }
      return matcher.group(1);
    }
  }

  private final List<Sent> sent = new CopyOnWriteArrayList<>();
  private volatile boolean failing;

  public List<Sent> sent() {
    return sent;
  }

  public Sent last() {
    return sent.getLast();
  }

  /** Makes every send throw, like a mail server that is down. */
  public void setFailing(boolean failing) {
    this.failing = failing;
  }

  public void reset() {
    sent.clear();
    failing = false;
  }

  @Override
  protected void doSend(MimeMessage[] messages, Object[] originalMessages) {
    if (failing) {
      throw new MailSendException("Mail server is down (simulated)");
    }
    try {
      for (MimeMessage message : messages) {
        message.saveChanges();
        StringBuilder text = new StringBuilder();
        StringBuilder html = new StringBuilder();
        collect(message, text, html);
        sent.add(
            new Sent(
                ((InternetAddress) message.getFrom()[0]).getAddress(),
                ((InternetAddress) message.getAllRecipients()[0]).getAddress(),
                message.getSubject(),
                text.toString(),
                html.toString()));
      }
    } catch (Exception e) {
      throw new MailSendException("Could not read the test message", e);
    }
  }

  private static void collect(Part part, StringBuilder text, StringBuilder html) throws Exception {
    if (part.isMimeType("text/plain") && text.isEmpty()) {
      text.append((String) part.getContent());
    } else if (part.isMimeType("text/html") && html.isEmpty()) {
      html.append((String) part.getContent());
    } else if (part.isMimeType("multipart/*")) {
      Multipart multipart = (Multipart) part.getContent();
      for (int i = 0; i < multipart.getCount(); i++) {
        collect(multipart.getBodyPart(i), text, html);
      }
    }
  }
}

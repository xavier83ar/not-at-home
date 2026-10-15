package com.notathome.mail;

import org.springframework.web.util.HtmlUtils;

/**
 * The site's emails. Plain HTML with inline styles, because mail clients ignore stylesheets, and
 * the brand colors (Pine for buttons, black text). Everything that comes from a user is escaped.
 */
public final class EmailTemplates {

  /** Content-ID of the inline logo attached by {@link MailService}. */
  static final String LOGO_CID = "logo";

  private static final String PINE = "#1F4D3A";
  private static final String INK = "#111111";

  private EmailTemplates() {}

  public static EmailMessage verifyEmail(String displayName, String verifyUrl) {
    String subject = "Verify your email address";
    String text =
        """
        Hi %s,

        Welcome to Not at Home! Confirm your email address to start posting:

        %s

        The link works for 24 hours. If you didn't create an account, you can ignore this email.
        """
            .formatted(displayName, verifyUrl);
    String html =
        layout(
            subject,
            paragraph("Hi " + escape(displayName) + ",")
                + paragraph("Welcome to Not at Home! Confirm your email address to start posting.")
                + button("Verify my email", verifyUrl)
                + small(
                    "The link works for 24 hours. If you didn't create an account, you can ignore"
                        + " this email.")
                + small(
                    "Button not working? Paste this link into your browser:<br>"
                        + link(verifyUrl)));
    return new EmailMessage(subject, text, html);
  }

  public static EmailMessage resetPassword(String displayName, String resetUrl) {
    String subject = "Reset your password";
    String text =
        """
        Hi %s,

        Someone asked to reset the password of your Not at Home account. To choose a new one, open:

        %s

        The link works for 1 hour and only once. If you didn't ask for this, ignore this email: \
        your password stays the same.
        """
            .formatted(displayName, resetUrl);
    String html =
        layout(
            subject,
            paragraph("Hi " + escape(displayName) + ",")
                + paragraph(
                    "Someone asked to reset the password of your Not at Home account. To choose a"
                        + " new one, use the button below.")
                + button("Choose a new password", resetUrl)
                + small(
                    "The link works for 1 hour and only once. If you didn't ask for this, ignore"
                        + " this email: your password stays the same.")
                + small(
                    "Button not working? Paste this link into your browser:<br>" + link(resetUrl)));
    return new EmailMessage(subject, text, html);
  }

  private static String layout(String title, String content) {
    return """
        <!DOCTYPE html>
        <html lang="en">
        <head><meta charset="utf-8"><meta name="viewport" content="width=device-width">\
        <title>%s</title></head>
        <body style="margin:0;padding:0;background:#f4f4f2;">
        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f2;padding:24px 12px;">
        <tr><td align="center">
        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:560px;background:#ffffff;border-radius:8px;border-top:4px solid %s;">
        <tr><td style="padding:28px 32px 8px 32px;"><img src="cid:%s" alt="Not at Home" width="200" style="display:block;border:0;height:auto;"></td></tr>
        <tr><td style="padding:8px 32px 32px 32px;font-family:Barlow,Arial,Helvetica,sans-serif;font-size:16px;line-height:1.5;color:%s;">%s</td></tr>
        </table>
        <p style="font-family:Arial,Helvetica,sans-serif;font-size:12px;color:#6b6b6b;margin:16px 0 0 0;">Not at Home &middot; Stories from the trail</p>
        </td></tr>
        </table>
        </body>
        </html>
        """
        .formatted(escape(title), PINE, LOGO_CID, INK, content);
  }

  private static String paragraph(String html) {
    return "<p style=\"margin:0 0 16px 0;\">" + html + "</p>";
  }

  private static String small(String html) {
    return "<p style=\"margin:16px 0 0 0;font-size:13px;color:#555555;\">" + html + "</p>";
  }

  private static String button(String label, String url) {
    return "<p style=\"margin:24px 0;\"><a href=\""
        + escape(url)
        + "\" style=\"display:inline-block;background:"
        + PINE
        + ";color:#ffffff;text-decoration:none;font-weight:600;padding:12px 24px;"
        + "border-radius:6px;\">"
        + escape(label)
        + "</a></p>";
  }

  private static String link(String url) {
    return "<a href=\""
        + escape(url)
        + "\" style=\"color:"
        + PINE
        + ";word-break:break-all;\">"
        + escape(url)
        + "</a>";
  }

  private static String escape(String text) {
    return HtmlUtils.htmlEscape(text);
  }
}

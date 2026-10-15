package com.notathome.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailTemplatesTests {

  static final String URL = "http://localhost:3000/verify-email?token=abc_-123";

  @Test
  void verificationEmailHasMatchingTextAndHtml() {
    EmailMessage message = EmailTemplates.verifyEmail("Alice", URL);

    assertThat(message.subject()).isEqualTo("Verify your email address");
    assertThat(message.text()).contains("Hi Alice,", URL, "24 hours");
    assertThat(message.html())
        .contains("Hi Alice,", "Verify my email", "cid:logo", "#1F4D3A")
        .contains("href=\"" + URL + "\"");
  }

  @Test
  void userSuppliedTextIsEscapedInHtmlButNotInThePlainText() {
    String name = "<script>alert('x')</script> & \"Co\"";

    EmailMessage message = EmailTemplates.verifyEmail(name, URL);

    assertThat(message.html())
        .doesNotContain("<script>")
        .contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; &quot;Co&quot;");
    assertThat(message.text()).contains(name);
  }

  @Test
  void urlsAreEscapedInAttributes() {
    EmailMessage message = EmailTemplates.verifyEmail("Alice", "http://x.test/?a=1&b=\"2\"");

    assertThat(message.html()).contains("href=\"http://x.test/?a=1&amp;b=&quot;2&quot;\"");
  }
}

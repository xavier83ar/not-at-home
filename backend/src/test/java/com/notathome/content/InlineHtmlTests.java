package com.notathome.content;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InlineHtmlTests {

  @Test
  void keepsTheFormattingEditorJsProduces() {
    String html = "<b>bold</b> <i>italic</i> <u>under</u> <code>code</code> <mark>marked</mark>";

    assertThat(InlineHtml.sanitize(html)).isEqualTo(html);
  }

  @Test
  void dropsScriptsHandlersAndUnknownTags() {
    String html =
        "<script>alert(1)</script><b onclick=\"x()\">hi</b><img src=x onerror=y><div>d</div>";

    assertThat(InlineHtml.sanitize(html)).isEqualTo("<b>hi</b>d");
  }

  @Test
  void dropsClassesAndStyles() {
    assertThat(InlineHtml.sanitize("<mark class=\"cdx-marker\" style=\"color:red\">m</mark>"))
        .isEqualTo("<mark>m</mark>");
  }

  @Test
  void keepsSafeLinks() {
    assertThat(InlineHtml.sanitize("<a href=\"https://example.com/a?b=1\">x</a>"))
        .contains("href=\"https://example.com/a?b&#61;1\"");
    assertThat(InlineHtml.sanitize("<a href=\"/topics/1-a?post=5\">x</a>"))
        .contains("href=\"/topics/1-a?post&#61;5\"");
    assertThat(InlineHtml.sanitize("<a href=\"mailto:a@example.com\">x</a>")).contains("href=");
  }

  @Test
  void dropsDangerousLinks() {
    for (String href :
        new String[] {
          "javascript:alert(1)",
          " JaVaScRiPt:alert(1)",
          "data:text/html,<script>1</script>",
          "//evil.example/x",
          "\\\\evil.example",
          "ftp://example.com",
          "https:///nohost"
        }) {
      assertThat(InlineHtml.sanitize("<a href=\"" + href + "\">x</a>"))
          .as(href)
          .doesNotContain("href");
    }
  }

  @Test
  void plainTextRemovesTagsAndDecodesEntities() {
    assertThat(InlineHtml.plainText("<b>he</b>llo&nbsp;wor&amp;ld<br>next"))
        .isEqualTo("hello wor&ld next");
  }

  @Test
  void plainTextOfOnlyTagsIsEmpty() {
    assertThat(InlineHtml.plainText("<b></b>&nbsp; <br>")).isEmpty();
  }
}

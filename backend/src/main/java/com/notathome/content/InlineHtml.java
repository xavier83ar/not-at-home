package com.notathome.content;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;
import org.owasp.html.AttributePolicy;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.web.util.HtmlUtils;

/**
 * The HTML Editor.js puts inside a block's text (bold, italic, links...). Only a short list of tags
 * is kept, without attributes except a safe {@code href} on links; everything else is dropped.
 */
final class InlineHtml {

  private static final Pattern TAG = Pattern.compile("<[^>]*>");
  private static final Pattern LINE_BREAK = Pattern.compile("(?i)<br\\s*/?>");
  private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00A0]+");

  /**
   * Links may go to this site (a path starting with a single slash, which is what quotes use) or to
   * an http(s) or mailto address. {@code javascript:}, {@code data:} and protocol-relative links
   * are dropped.
   */
  private static final AttributePolicy SAFE_HREF =
      (elementName, attributeName, value) -> isSafeHref(value.strip()) ? value.strip() : null;

  private static final PolicyFactory POLICY =
      new HtmlPolicyBuilder()
          .allowUrlProtocols("http", "https", "mailto")
          .allowElements("b", "strong", "i", "em", "u", "code", "mark", "br", "a")
          .allowAttributes("href")
          .matching(SAFE_HREF)
          .onElements("a")
          .toFactory();

  private InlineHtml() {}

  static String sanitize(String html) {
    return POLICY.sanitize(html);
  }

  /** Whether {@code url} is allowed as the target of a link (see {@link #SAFE_HREF}). */
  static boolean isSafeHref(String url) {
    if (url.isEmpty() || url.length() > 2000 || url.indexOf('\\') >= 0 || hasControlChars(url)) {
      return false;
    }
    if (url.startsWith("/")) {
      return !url.startsWith("//");
    }
    return isAbsolute(url, "http", "https", "mailto");
  }

  /** Whether {@code url} is an absolute http(s) address, e.g. for the "link" block. */
  static boolean isWebUrl(String url) {
    return url.length() <= 2000
        && url.indexOf('\\') < 0
        && !hasControlChars(url)
        && isAbsolute(url, "http", "https");
  }

  private static boolean isAbsolute(String url, String... schemes) {
    try {
      URI uri = new URI(url);
      for (String scheme : schemes) {
        if (scheme.equalsIgnoreCase(uri.getScheme())) {
          return !scheme.startsWith("http") || uri.getHost() != null;
        }
      }
      return false;
    } catch (URISyntaxException e) {
      return false;
    }
  }

  private static boolean hasControlChars(String text) {
    return text.chars().anyMatch(c -> c < 0x20 || c == 0x7F);
  }

  /**
   * The text of already sanitized HTML, with tags removed and entities decoded. Used for full-text
   * search and to tell empty blocks from real ones; it is never shown, so it needs no escaping.
   */
  static String plainText(String sanitizedHtml) {
    String withBreaks = LINE_BREAK.matcher(sanitizedHtml).replaceAll(" ");
    String withoutTags = TAG.matcher(withBreaks).replaceAll("");
    return WHITESPACE.matcher(HtmlUtils.htmlUnescape(withoutTags)).replaceAll(" ").strip();
  }
}

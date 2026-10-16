package com.notathome.common;

import java.text.Normalizer;
import java.util.Locale;

/** Turns a name or title into a URL-friendly slug (e.g. "Trip Reports" -> "trip-reports"). */
public final class Slug {

  private static final int MAX_LENGTH = 100;

  private Slug() {}

  /** {@code fallback} is used when nothing usable is left (e.g. a title made of emoji). */
  public static String of(String text, String fallback) {
    // Apostrophes vanish instead of splitting the word: "What's" is "whats", not "what-s".
    String ascii =
        Normalizer.normalize(text, Normalizer.Form.NFKD)
            .replaceAll("\\p{M}", "")
            .replaceAll("['\u2019]", "");
    String slug =
        ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    if (slug.length() > MAX_LENGTH) {
      slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", "");
    }
    return slug.isEmpty() ? fallback : slug;
  }
}

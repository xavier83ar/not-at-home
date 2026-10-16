package com.notathome.categories;

import java.text.Normalizer;
import java.util.Locale;

/** Turns a category name into a URL-friendly slug (e.g. "Trip Reports" -> "trip-reports"). */
final class Slug {

  private static final int MAX_LENGTH = 100;

  private Slug() {}

  static String of(String text) {
    String ascii = Normalizer.normalize(text, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
    String slug =
        ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    if (slug.length() > MAX_LENGTH) {
      slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", "");
    }
    return slug.isEmpty() ? "category" : slug;
  }
}

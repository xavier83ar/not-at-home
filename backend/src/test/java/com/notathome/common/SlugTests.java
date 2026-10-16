package com.notathome.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SlugTests {

  @Test
  void turnsTitlesIntoUrlFriendlyWords() {
    assertThat(Slug.of("Best trails in Patagonia", "topic")).isEqualTo("best-trails-in-patagonia");
    assertThat(Slug.of("  Trip   Reports!! ", "topic")).isEqualTo("trip-reports");
  }

  @Test
  void dropsAccentsAndApostrophesInsteadOfSplittingWords() {
    assertThat(Slug.of("What's in your pack?", "topic")).isEqualTo("whats-in-your-pack");
    assertThat(Slug.of("It’s Chaltén time", "topic")).isEqualTo("its-chalten-time");
  }

  @Test
  void usesTheFallbackWhenNothingIsLeft() {
    assertThat(Slug.of("!!!", "topic")).isEqualTo("topic");
    assertThat(Slug.of("🏔️", "category")).isEqualTo("category");
  }

  @Test
  void isNeverLongerThanTheColumn() {
    String slug = Slug.of("word ".repeat(100), "topic");
    assertThat(slug.length()).isLessThanOrEqualTo(100);
    assertThat(slug).doesNotEndWith("-");
  }
}

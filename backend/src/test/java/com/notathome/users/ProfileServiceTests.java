package com.notathome.users;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProfileServiceTests {

  @Test
  void anExcerptOfShortTextIsTheTextOnOneLine() {
    assertThat(ProfileService.excerpt("First line\n  second   line\n"))
        .isEqualTo("First line second line");
  }

  @Test
  void aLongExcerptIsCutAtTheLimitWithAnEllipsis() {
    String excerpt = ProfileService.excerpt("word ".repeat(100));

    assertThat(excerpt).endsWith("…");
    assertThat(excerpt.length()).isEqualTo(ProfileService.EXCERPT_CHARS);
  }

  @Test
  void anExcerptNeverCutsACharacterInHalf() {
    // Each emoji is two chars (a surrogate pair); an odd limit counted in chars would split one.
    String excerpt = ProfileService.excerpt("🏔".repeat(ProfileService.EXCERPT_CHARS + 5));

    assertThat(excerpt).isEqualTo("🏔".repeat(ProfileService.EXCERPT_CHARS) + "…");
  }
}

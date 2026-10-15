package com.notathome.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SecureTokensTests {

  @Test
  void tokensAreUrlSafeAndUnique() {
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < 1000; i++) {
      String token = SecureTokens.generate();
      assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
      assertThat(seen.add(token)).isTrue();
    }
  }

  @Test
  void hashIsStableLowercaseHexOfFixedLength() {
    String hash = SecureTokens.hash("some-token");

    assertThat(hash).hasSize(64).matches("[0-9a-f]+").isEqualTo(SecureTokens.hash("some-token"));
    assertThat(hash).isNotEqualTo(SecureTokens.hash("some-token2"));
    // Known SHA-256 value, so a change of algorithm is noticed.
    assertThat(SecureTokens.hash("abc"))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }
}

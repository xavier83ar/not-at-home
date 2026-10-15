package com.notathome.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PasswordResetServiceTests {

  /**
   * Asking for a reset must take the same time for every address (FR-1.8), so the call itself may
   * do nothing but hand the address to the handler that runs on another thread. This service has no
   * repositories at all: if {@code request} touched one, it would fail here.
   */
  @Test
  void askingForAResetOnlyHandsTheAddressOver() {
    List<Object> published = new ArrayList<>();
    PasswordResetService service = new PasswordResetService(null, null, null, null, published::add);

    service.request("someone@example.com");

    assertThat(published).containsExactly(new PasswordResetRequested("someone@example.com"));
  }
}

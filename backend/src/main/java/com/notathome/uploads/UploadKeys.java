package com.notathome.uploads;

import java.security.SecureRandom;
import java.util.Base64;

/** Random file names, unguessable so there is no need to hide the uploads directory itself. */
final class UploadKeys {

  private static final SecureRandom RANDOM = new SecureRandom();

  private UploadKeys() {}

  static String generate() {
    byte[] bytes = new byte[24];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}

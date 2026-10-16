package com.notathome.uploads;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * The image formats accepted for uploads (FR-4.4): detected from the file's bytes, not its name or
 * declared content type, so a renamed script can't pass as an image.
 */
enum ImageType {
  JPEG("image/jpeg", "jpg"),
  PNG("image/png", "png"),
  WEBP("image/webp", "webp");

  private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
  private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

  private final String contentType;
  private final String extension;

  ImageType(String contentType, String extension) {
    this.contentType = contentType;
    this.extension = extension;
  }

  String contentType() {
    return contentType;
  }

  String extension() {
    return extension;
  }

  static Optional<ImageType> detect(byte[] content) {
    if (startsWith(content, JPEG_MAGIC)) {
      return Optional.of(JPEG);
    }
    if (startsWith(content, PNG_MAGIC)) {
      return Optional.of(PNG);
    }
    if (isWebp(content)) {
      return Optional.of(WEBP);
    }
    return Optional.empty();
  }

  private static boolean isWebp(byte[] content) {
    if (content.length < 12) {
      return false;
    }
    String riff = new String(content, 0, 4, StandardCharsets.US_ASCII);
    String webp = new String(content, 8, 4, StandardCharsets.US_ASCII);
    return riff.equals("RIFF") && webp.equals("WEBP");
  }

  private static boolean startsWith(byte[] content, byte[] magic) {
    if (content.length < magic.length) {
      return false;
    }
    for (int i = 0; i < magic.length; i++) {
      if (content[i] != magic[i]) {
        return false;
      }
    }
    return true;
  }
}

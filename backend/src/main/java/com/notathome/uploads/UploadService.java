package com.notathome.uploads;

import com.notathome.common.FieldErrorsException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadService {

  static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

  private final UploadRepository uploads;
  private final UploadStorage storage;

  UploadService(UploadRepository uploads, UploadStorage storage) {
    this.uploads = uploads;
    this.storage = storage;
  }

  /** Validates by content (FR-4.4, NFR-2) and stores under a random name. */
  @Transactional
  Upload store(MultipartFile file, Long ownerId) {
    if (file.isEmpty()) {
      throw fieldError("is required");
    }
    return storeValidated(readBytes(file), ownerId);
  }

  /**
   * For content that doesn't come from an HTTP request (seed images bundled with the app), so
   * there's no {@link MultipartFile} to read from. Still runs the same checks: a seed asset that
   * isn't really a JPEG, PNG or WebP under 5 MB is a bug worth failing loudly on, not storing.
   */
  @Transactional
  public Upload storeTrusted(byte[] content, Long ownerId) {
    return storeValidated(content, ownerId);
  }

  private Upload storeValidated(byte[] content, Long ownerId) {
    if (content.length > MAX_SIZE_BYTES) {
      throw fieldError("must be 5 MB or smaller");
    }
    ImageType type =
        ImageType.detect(content)
            .orElseThrow(() -> fieldError("must be a JPEG, PNG or WebP image"));

    String key = UploadKeys.generate() + "." + type.extension();
    try {
      storage.store(key, content);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return uploads.save(new Upload(ownerId, key, type.contentType(), content.length));
  }

  private static byte[] readBytes(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static FieldErrorsException fieldError(String message) {
    return new FieldErrorsException(Map.of("file", List.of(message)));
  }
}

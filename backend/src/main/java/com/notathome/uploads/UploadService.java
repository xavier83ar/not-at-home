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
class UploadService {

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
    byte[] content = readBytes(file);
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

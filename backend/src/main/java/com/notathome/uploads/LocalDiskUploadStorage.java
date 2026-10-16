package com.notathome.uploads;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
class LocalDiskUploadStorage implements UploadStorage {

  private final Path root;

  LocalDiskUploadStorage(UploadProperties properties) {
    this.root = Path.of(properties.dir()).toAbsolutePath().normalize();
    try {
      Files.createDirectories(root);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void store(String key, byte[] content) throws IOException {
    Files.write(resolve(key), content);
  }

  @Override
  public Resource load(String key) {
    return new FileSystemResource(resolve(key));
  }

  /**
   * Keys only ever come from {@link UploadKeys#generate()} or a round trip through the database, so
   * this never sees a traversal attempt in practice; the check is defense in depth for a path that
   * serves files straight to the browser.
   */
  private Path resolve(String key) {
    Path resolved = root.resolve(key).normalize();
    if (!resolved.startsWith(root)) {
      throw new IllegalArgumentException("Invalid upload key: " + key);
    }
    return resolved;
  }
}

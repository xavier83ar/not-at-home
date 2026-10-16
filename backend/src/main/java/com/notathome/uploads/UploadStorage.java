package com.notathome.uploads;

import java.io.IOException;
import org.springframework.core.io.Resource;

/**
 * Where uploaded files live. Local disk is the only implementation for now (file storage for
 * production is TBD, see the plan); the rest of the code only depends on this interface.
 */
public interface UploadStorage {

  void store(String key, byte[] content) throws IOException;

  Resource load(String key);
}

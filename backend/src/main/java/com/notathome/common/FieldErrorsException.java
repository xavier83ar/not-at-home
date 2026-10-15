package com.notathome.common;

import java.util.List;
import java.util.Map;

/**
 * A request failed validation in a way only the service can tell (e.g. a username that is taken).
 * Rendered like bean validation failures: a 400 with an {@code errors} map of field to messages.
 */
public class FieldErrorsException extends RuntimeException {

  private final transient Map<String, List<String>> errors;

  public FieldErrorsException(Map<String, List<String>> errors) {
    super("Validation failed");
    this.errors = errors;
  }

  public Map<String, List<String>> getErrors() {
    return errors;
  }
}

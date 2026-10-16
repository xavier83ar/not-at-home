package com.notathome.uploads;

/**
 * Where an upload is served from. Other features (categories, avatars) store the upload's id, not
 * this URL, and build it with this when they render a response.
 */
public final class UploadUrl {

  private UploadUrl() {}

  public static String of(Upload upload) {
    return of(upload.getStorageKey());
  }

  public static String of(String storageKey) {
    return "/api/uploads/" + storageKey;
  }
}

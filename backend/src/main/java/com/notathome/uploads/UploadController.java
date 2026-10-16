package com.notathome.uploads;

import com.notathome.auth.AuthUser;
import com.notathome.ratelimit.RateLimitPolicy;
import com.notathome.ratelimit.RateLimited;
import java.time.Duration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
class UploadController {

  private final UploadService uploadService;
  private final UploadRepository uploads;
  private final UploadStorage storage;

  UploadController(UploadService uploadService, UploadRepository uploads, UploadStorage storage) {
    this.uploadService = uploadService;
    this.uploads = uploads;
    this.storage = storage;
  }

  /** Any logged-in user may upload (avatars and post images don't need a verified email). */
  @PostMapping(path = "/api/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @RateLimited(RateLimitPolicy.UPLOAD)
  @ResponseStatus(HttpStatus.CREATED)
  UploadResponse upload(
      @RequestParam("file") MultipartFile file, @AuthenticationPrincipal AuthUser principal) {
    return UploadResponse.from(uploadService.store(file, principal.getId()));
  }

  /**
   * Public: category covers and avatars must be visible to guests. The headers stop the browser
   * from ever treating the response as HTML or a script (NFR-2); the name is random, so it can be
   * cached for a long time.
   */
  @GetMapping("/api/uploads/{key}")
  ResponseEntity<Resource> serve(@PathVariable String key) {
    Upload upload =
        uploads
            .findByStorageKey(key)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(upload.getContentType()))
        .header("Content-Security-Policy", "sandbox")
        .header("X-Content-Type-Options", "nosniff")
        .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
        .body(storage.load(upload.getStorageKey()));
  }
}

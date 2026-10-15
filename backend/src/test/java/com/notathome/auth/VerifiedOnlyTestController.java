package com.notathome.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Stands in for the posting endpoints (M4), which will carry the same annotation. */
@RestController
class VerifiedOnlyTestController {

  @RequiresVerifiedEmail
  @GetMapping("/api/test/verified-only")
  String verifiedOnly() {
    return "ok";
  }

  @GetMapping("/api/test/open-to-all-users")
  String openToAllUsers() {
    return "ok";
  }
}

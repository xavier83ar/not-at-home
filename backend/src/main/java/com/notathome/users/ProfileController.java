package com.notathome.users;

import com.notathome.auth.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ProfileController {

  private final ProfileService profiles;

  ProfileController(ProfileService profiles) {
    this.profiles = profiles;
  }

  /** FR-8.1: public, like the posts it shows. */
  @GetMapping("/api/users/{username}")
  UserProfileResponse get(@PathVariable String username) {
    return profiles.get(username);
  }

  /**
   * FR-8.2. Editing a profile doesn't need a verified email (section 5.4), and the settings page
   * reads the current values from the public profile, so these answer with nothing.
   */
  @PutMapping("/api/me/profile")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void update(
      @Valid @RequestBody UpdateProfileRequest request, @AuthenticationPrincipal AuthUser user) {
    profiles.update(user.getId(), request);
  }

  @PutMapping("/api/me/avatar")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void changeAvatar(
      @Valid @RequestBody AvatarRequest request, @AuthenticationPrincipal AuthUser user) {
    profiles.changeAvatar(user.getId(), request.uploadId());
  }

  @DeleteMapping("/api/me/avatar")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void removeAvatar(@AuthenticationPrincipal AuthUser user) {
    profiles.removeAvatar(user.getId());
  }
}

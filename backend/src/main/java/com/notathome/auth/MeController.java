package com.notathome.auth;

import com.notathome.users.UserRepository;
import com.notathome.users.UserStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class MeController {

  private final UserRepository users;

  MeController(UserRepository users) {
    this.users = users;
  }

  /**
   * The current user, or 401. The frontend calls this on every server-side render. The user is read
   * from the database each time, so a ban or deletion takes effect at once and the stale session is
   * dropped.
   */
  @GetMapping("/api/me")
  CurrentUserResponse me(@AuthenticationPrincipal AuthUser principal, HttpServletRequest request) {
    return users
        .findByIdAndDeletedAtIsNull(principal.getId())
        .filter(user -> user.getStatus() == UserStatus.ACTIVE)
        .map(CurrentUserResponse::from)
        .orElseThrow(
            () -> {
              HttpSession session = request.getSession(false);
              if (session != null) {
                session.invalidate();
              }
              return new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            });
  }
}

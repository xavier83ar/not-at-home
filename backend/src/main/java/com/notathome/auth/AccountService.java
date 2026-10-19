package com.notathome.auth;

import com.notathome.common.FieldErrorsException;
import com.notathome.posts.AuthorPosts;
import com.notathome.users.LastAdminException;
import com.notathome.users.User;
import com.notathome.users.UserRepository;
import com.notathome.users.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Changes to the logged-in user's own account that need their password (FR-8.3, FR-8.4). A
 * logged-in browser is not proof enough for these: someone may be using a session that isn't
 * theirs.
 */
@Service
class AccountService {

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final UserSessions userSessions;
  private final EmailVerificationService verification;
  private final EmailVerificationTokenRepository emailTokens;
  private final PasswordResetTokenRepository resetTokens;
  private final AuthorPosts authorPosts;

  AccountService(
      UserRepository users,
      PasswordEncoder passwordEncoder,
      UserSessions userSessions,
      EmailVerificationService verification,
      EmailVerificationTokenRepository emailTokens,
      PasswordResetTokenRepository resetTokens,
      AuthorPosts authorPosts) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.userSessions = userSessions;
    this.verification = verification;
    this.emailTokens = emailTokens;
    this.resetTokens = resetTokens;
    this.authorPosts = authorPosts;
  }

  /**
   * FR-8.3. Everyone else logged in as this user is logged out, remembered logins included, and
   * reset links that were already asked for stop working: whoever knew the old password may be
   * behind any of them.
   */
  @Transactional
  void changePassword(
      Long userId, String currentPassword, String newPassword, String currentSessionId) {
    User user = confirmed(userId, currentPassword, "currentPassword");
    user.changePasswordHash(passwordEncoder.encode(newPassword));
    resetTokens.deleteByUserIdAndUsedAtIsNull(userId);
    userSessions.endOthers(user.getUsername(), currentSessionId);
  }

  /**
   * FR-8.3: sends a link to the new address; the account keeps its current one until that link is
   * used. Allowed for unverified accounts too, since a mistyped address is the usual reason they
   * are unverified.
   */
  @Transactional
  void requestEmailChange(Long userId, String currentPassword, String rawEmail) {
    User user = confirmed(userId, currentPassword, "currentPassword");
    String email = rawEmail.strip();
    if (email.equalsIgnoreCase(user.getEmail())) {
      throw fieldError("email", "is already your email address");
    }
    if (users.existsByEmailIgnoreCase(email)) {
      throw fieldError("email", "is already registered");
    }
    verification.requestEmailChange(user, email);
  }

  /**
   * FR-8.4 and FR-8.5. Their posts are deleted first, then the account is anonymized and every
   * session ended, the caller's too. The sessions are found by username, so that happens before the
   * username is replaced.
   */
  @Transactional
  void deleteAccount(Long userId, String password) {
    User user = confirmed(userId, password, "password");
    if (user.getRole() == UserRole.ADMIN) {
      List<User> admins = users.lockActiveAdmins();
      if (admins.size() == 1 && admins.getFirst().getId().equals(userId)) {
        throw new LastAdminException();
      }
    }
    Instant now = Instant.now();
    authorPosts.removeAll(userId, now);
    // They hold addresses, which a deleted account must not keep.
    emailTokens.deleteByUserId(userId);
    resetTokens.deleteByUserId(userId);
    String username = user.getUsername();
    user.anonymize(now);
    userSessions.endAll(username);
  }

  /**
   * The user, if {@code password} is theirs. An account with no password (signed up with Google,
   * once that exists) can't confirm this way, and M10 adds the way that it can.
   */
  private User confirmed(Long userId, String password, String field) {
    User user =
        users
            .findByIdAndDeletedAtIsNull(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    String hash = user.getPasswordHash();
    if (hash == null || !passwordEncoder.matches(password, hash)) {
      throw fieldError(field, "is incorrect");
    }
    return user;
  }

  private static FieldErrorsException fieldError(String field, String message) {
    return new FieldErrorsException(Map.of(field, List.of(message)));
  }
}

package com.notathome.auth;

import com.notathome.common.FieldErrorsException;
import com.notathome.users.User;
import com.notathome.users.UserRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RegistrationService {

  private static final String USERNAME_TAKEN = "is already taken";
  private static final String EMAIL_TAKEN = "is already registered";

  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final EmailVerificationService verification;

  RegistrationService(
      UserRepository users,
      PasswordEncoder passwordEncoder,
      EmailVerificationService verification) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.verification = verification;
  }

  /** Creates the account and queues its verification email (FR-1.9). */
  @Transactional
  public User register(RegisterRequest request) {
    String username = request.username();
    String email = request.email().strip();

    Map<String, List<String>> errors = new LinkedHashMap<>();
    if (users.existsByUsernameIgnoreCase(username)) {
      errors.put("username", List.of(USERNAME_TAKEN));
    }
    if (users.existsByEmailIgnoreCase(email)) {
      errors.put("email", List.of(EMAIL_TAKEN));
    }
    if (!errors.isEmpty()) {
      throw new FieldErrorsException(errors);
    }

    User user =
        new User(
            username,
            email,
            request.displayName().strip(),
            passwordEncoder.encode(request.password()));
    try {
      // Flush now: two people registering the same name at once pass the checks above, and the
      // unique indexes (case-insensitive) are what actually settle it.
      users.saveAndFlush(user);
    } catch (DataIntegrityViolationException e) {
      throw new FieldErrorsException(uniqueViolation(e));
    }
    verification.sendVerificationEmail(user);
    return user;
  }

  private static Map<String, List<String>> uniqueViolation(DataIntegrityViolationException e) {
    String cause = String.valueOf(e.getMostSpecificCause().getMessage());
    if (cause.contains("users_username_lower_ux")) {
      return Map.of("username", List.of(USERNAME_TAKEN));
    }
    if (cause.contains("users_email_lower_ux")) {
      return Map.of("email", List.of(EMAIL_TAKEN));
    }
    throw e;
  }
}

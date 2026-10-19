package com.notathome.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

  Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

  /** Call inside a transaction. */
  void deleteByUserIdAndUsedAtIsNull(Long userId);

  /** Call inside a transaction. */
  void deleteByUserId(Long userId);

  /** The address change waiting for its link to be used, if any (the newest one). */
  Optional<EmailVerificationToken>
      findFirstByUserIdAndEmailChangeTrueAndUsedAtIsNullAndExpiresAtAfterOrderByIdDesc(
          Long userId, Instant now);
}

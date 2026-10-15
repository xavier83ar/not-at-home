package com.notathome.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

  Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

  /** Call inside a transaction. */
  void deleteByUserIdAndUsedAtIsNull(Long userId);
}

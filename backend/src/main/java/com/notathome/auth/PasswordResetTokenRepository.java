package com.notathome.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

  Optional<PasswordResetToken> findByTokenHash(String tokenHash);

  /** Call inside a transaction. */
  void deleteByUserIdAndUsedAtIsNull(Long userId);

  /**
   * Marks a token used, but only if nobody did already. The row count says whether this call is the
   * one that used it, so two requests with the same link can't both reset the password.
   */
  @Modifying
  @Query("update PasswordResetToken t set t.usedAt = :now where t.id = :id and t.usedAt is null")
  int markUsed(@Param("id") Long id, @Param("now") Instant now);
}

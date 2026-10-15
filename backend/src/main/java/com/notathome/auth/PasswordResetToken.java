package com.notathome.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "password_reset_tokens")
class PasswordResetToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long userId;
  private String tokenHash;
  private Instant expiresAt;
  private Instant usedAt;

  @Column(insertable = false, updatable = false)
  private Instant createdAt;

  protected PasswordResetToken() {}

  PasswordResetToken(Long userId, String tokenHash, Instant expiresAt) {
    this.userId = userId;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
  }

  Long getId() {
    return id;
  }

  Long getUserId() {
    return userId;
  }

  boolean isUsable(Instant now) {
    return usedAt == null && now.isBefore(expiresAt);
  }
}

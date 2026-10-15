package com.notathome.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "email_verification_tokens")
class EmailVerificationToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long userId;
  private String email;
  private String tokenHash;
  private Instant expiresAt;
  private Instant usedAt;

  @Column(insertable = false, updatable = false)
  private Instant createdAt;

  protected EmailVerificationToken() {}

  EmailVerificationToken(Long userId, String email, String tokenHash, Instant expiresAt) {
    this.userId = userId;
    this.email = email;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
  }

  Long getUserId() {
    return userId;
  }

  String getEmail() {
    return email;
  }

  boolean isUsable(Instant now) {
    return usedAt == null && now.isBefore(expiresAt);
  }

  void markUsed(Instant now) {
    this.usedAt = now;
  }
}

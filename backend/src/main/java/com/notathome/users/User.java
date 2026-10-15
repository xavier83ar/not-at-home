package com.notathome.users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String username;
  private String email;
  private Instant emailVerifiedAt;
  private String passwordHash;
  private String displayName;
  private String avatarUrl;

  @Enumerated(EnumType.STRING)
  private UserRole role = UserRole.USER;

  @Enumerated(EnumType.STRING)
  private UserStatus status = UserStatus.ACTIVE;

  @Column(insertable = false, updatable = false)
  private Instant createdAt;

  private Instant deletedAt;

  protected User() {}

  public User(String username, String email, String displayName, String passwordHash) {
    this.username = username;
    this.email = email;
    this.displayName = displayName;
    this.passwordHash = passwordHash;
  }

  public Long getId() {
    return id;
  }

  public String getUsername() {
    return username;
  }

  public String getEmail() {
    return email;
  }

  public Instant getEmailVerifiedAt() {
    return emailVerifiedAt;
  }

  public boolean isEmailVerified() {
    return emailVerifiedAt != null;
  }

  public void markEmailVerified(Instant at) {
    this.emailVerifiedAt = at;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getAvatarUrl() {
    return avatarUrl;
  }

  public UserRole getRole() {
    return role;
  }

  public void setRole(UserRole role) {
    this.role = role;
  }

  public UserStatus getStatus() {
    return status;
  }

  public void setStatus(UserStatus status) {
    this.status = status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }
}

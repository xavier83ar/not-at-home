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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

  /** Editor.js JSON, already validated and sanitized (see {@code EditorContentProcessor}). */
  @JdbcTypeCode(SqlTypes.JSON)
  private String profileContent;

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

  public void changePasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getAvatarUrl() {
    return avatarUrl;
  }

  public String getProfileContent() {
    return profileContent;
  }

  /** FR-8.2. A null {@code profileContent} clears the profile text. */
  public void updateProfile(String displayName, String profileContent) {
    this.displayName = displayName;
    this.profileContent = profileContent;
  }

  /** FR-8.2. A null URL goes back to the generated initials avatar. */
  public void changeAvatar(String avatarUrl) {
    this.avatarUrl = avatarUrl;
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

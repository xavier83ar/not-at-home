package com.notathome.auth;

import com.notathome.users.User;
import com.notathome.users.UserRole;
import com.notathome.users.UserStatus;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The principal kept in the HTTP session. It is serialized into the session table, so it holds only
 * what authorization needs and never a JPA entity. Everything else is read from the database when
 * needed (see {@code GET /api/me}).
 *
 * <p>{@link #getUsername()} is the user's username, which can't change (FR-1.2), so it is the
 * stable key for both Spring Session's principal index and remember-me tokens.
 */
public final class AuthUser implements UserDetails, CredentialsContainer, Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private final Long id;
  private final String username;
  private final UserRole role;
  private final boolean banned;
  private String passwordHash;

  public AuthUser(User user) {
    this.id = user.getId();
    this.username = user.getUsername();
    this.role = user.getRole();
    this.banned = user.getStatus() == UserStatus.BANNED;
    this.passwordHash = user.getPasswordHash();
  }

  public Long getId() {
    return id;
  }

  public UserRole getRole() {
    return role;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return username;
  }

  @Override
  public boolean isAccountNonLocked() {
    return !banned;
  }

  @Override
  public void eraseCredentials() {
    this.passwordHash = null;
  }
}

package com.notathome.auth;

import com.notathome.users.User;
import com.notathome.users.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Loads users for password login by username or email, ignoring case (FR-1.4). */
@Service
class AuthUserDetailsService implements UserDetailsService {

  private final UserRepository users;

  AuthUserDetailsService(UserRepository users) {
    this.users = users;
  }

  @Override
  @Transactional(readOnly = true)
  public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
    User user =
        users
            .findByUsernameIgnoreCaseAndDeletedAtIsNull(login)
            .or(() -> users.findByEmailIgnoreCaseAndDeletedAtIsNull(login))
            .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    return new AuthUser(user);
  }
}

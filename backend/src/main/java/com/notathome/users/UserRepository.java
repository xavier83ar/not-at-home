package com.notathome.users;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByUsernameIgnoreCaseAndDeletedAtIsNull(String username);

  Optional<User> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

  Optional<User> findByIdAndDeletedAtIsNull(Long id);
}

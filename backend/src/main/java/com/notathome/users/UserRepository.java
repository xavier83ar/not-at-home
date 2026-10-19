package com.notathome.users;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByUsernameIgnoreCaseAndDeletedAtIsNull(String username);

  Optional<User> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

  Optional<User> findByIdAndDeletedAtIsNull(Long id);

  /**
   * The admins who can still act (not banned, not deleted), locked until the transaction ends. Code
   * that removes an admin (deleting an account, demoting) takes this first and counts the result,
   * so two admins can't both leave at the same time and empty the list (FR-8.5, FR-9.4).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select u from User u where u.role = com.notathome.users.UserRole.ADMIN"
          + " and u.status = com.notathome.users.UserStatus.ACTIVE and u.deletedAt is null"
          + " order by u.id")
  List<User> lockActiveAdmins();

  /** Includes deleted accounts: their rows keep (anonymized) values that the unique indexes see. */
  boolean existsByUsernameIgnoreCase(String username);

  boolean existsByEmailIgnoreCase(String email);
}

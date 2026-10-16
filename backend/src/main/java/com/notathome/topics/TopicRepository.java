package com.notathome.topics;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TopicRepository extends JpaRepository<Topic, Long> {

  Optional<Topic> findByIdAndDeletedAtIsNull(Long id);

  /**
   * Locks the topic's row until the transaction ends. Whoever changes a topic's posts takes this
   * first, so the counters they recompute never come from a stale view of a concurrent change.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from Topic t where t.id = :id and t.deletedAt is null")
  Optional<Topic> findActiveForUpdate(@Param("id") Long id);

  /** Pinned topics first, then the most recently active (FR-3.3). */
  Page<Topic> findByCategoryIdAndDeletedAtIsNullOrderByPinnedDescLastPostAtDescIdDesc(
      Long categoryId, Pageable pageable);
}

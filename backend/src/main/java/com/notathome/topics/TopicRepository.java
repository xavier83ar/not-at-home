package com.notathome.topics;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
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

  /** FR-5.7: the most recently active topics of a category, pinned or not. */
  List<Topic> findTop5ByCategoryIdAndDeletedAtIsNullOrderByLastPostAtDescIdDesc(Long categoryId);

  /** The most recently active topics of the whole forum, for the all-topics page. */
  Page<Topic> findByDeletedAtIsNullOrderByLastPostAtDescIdDesc(Pageable pageable);

  /**
   * An author's newest topics for their profile (FR-8.1). A topic whose opening post the author
   * removed (FR-5.3) stays in its category but not here: its title is part of what they removed.
   */
  @Query(
      "select t from Topic t where t.authorId = :authorId and t.deletedAt is null"
          + " and not exists (select 1 from Post p where p.topicId = t.id and p.deletedAt is not null"
          + " and p.id = (select min(f.id) from Post f where f.topicId = t.id))"
          + " order by t.createdAt desc, t.id desc")
  List<Topic> recentByAuthor(@Param("authorId") Long authorId, Pageable pageable);

  /** FR-2.4: a category with active topics cannot be deleted. */
  boolean existsByCategoryIdAndDeletedAtIsNull(Long categoryId);

  record CategoryTopicStats(Long categoryId, long topics, Instant latestActivityAt) {}

  /** Topic count and latest activity of each category, for the category pages (FR-2.2). */
  @Query(
      "select new com.notathome.topics.TopicRepository$CategoryTopicStats("
          + "t.categoryId, count(t), max(t.lastPostAt))"
          + " from Topic t where t.deletedAt is null and t.categoryId in :categoryIds"
          + " group by t.categoryId")
  List<CategoryTopicStats> statsByCategory(@Param("categoryIds") Collection<Long> categoryIds);
}

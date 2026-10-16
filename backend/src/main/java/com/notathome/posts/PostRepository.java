package com.notathome.posts;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

  /** A topic's posts, oldest first (FR-4.3). Removed ones stay in the list, as placeholders. */
  Page<Post> findByTopicIdOrderByIdAsc(Long topicId, Pageable pageable);

  /** The opening post: the first one written, whether or not it has been removed since. */
  Optional<Post> findFirstByTopicIdOrderByIdAsc(Long topicId);

  /** How many posts come before this one in its topic, which is what tells its page (FR-4.6). */
  long countByTopicIdAndIdLessThan(Long topicId, Long id);

  long countByTopicIdAndDeletedAtIsNullAndIdNot(Long topicId, Long excludedId);

  @Query("select max(p.createdAt) from Post p where p.topicId = :topicId and p.deletedAt is null")
  Optional<Instant> latestVisibleAt(@Param("topicId") Long topicId);

  record LatestPost(Long topicId, Long postId) {}

  /** For each topic, its newest post that has not been removed (FR-7.2). */
  @Query(
      "select new com.notathome.posts.PostRepository$LatestPost(p.topicId, max(p.id))"
          + " from Post p where p.topicId in :topicIds and p.deletedAt is null group by p.topicId")
  List<LatestPost> latestVisiblePosts(@Param("topicIds") Collection<Long> topicIds);
}

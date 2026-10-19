package com.notathome.posts;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

  /** Read without loading the post, so the topic can be locked before the post itself is read. */
  @Query("select p.topicId from Post p where p.id = :id")
  Optional<Long> findTopicId(@Param("id") Long id);

  /** Removing a topic removes all its posts too (FR-5.4). */
  @Modifying
  @Query("update Post p set p.deletedAt = :at where p.topicId = :topicId and p.deletedAt is null")
  void removeAllOfTopic(@Param("topicId") Long topicId, @Param("at") Instant at);

  record CategoryPostCount(Long categoryId, long posts) {}

  /** Visible posts in each category's active topics, for the category pages (FR-2.2). */
  @Query(
      "select new com.notathome.posts.PostRepository$CategoryPostCount(t.categoryId, count(p))"
          + " from Post p join Topic t on p.topicId = t.id"
          + " where t.deletedAt is null and p.deletedAt is null and t.categoryId in :categoryIds"
          + " group by t.categoryId")
  List<CategoryPostCount> countByCategory(@Param("categoryIds") Collection<Long> categoryIds);

  record CategoryRecentPostCount(Long categoryId, long posts) {}

  /** Visible posts written since {@code since}, per category, for the home page's cards. */
  @Query(
      "select new com.notathome.posts.PostRepository$CategoryRecentPostCount(t.categoryId, count(p))"
          + " from Post p join Topic t on p.topicId = t.id"
          + " where t.deletedAt is null and p.deletedAt is null and p.createdAt >= :since"
          + " group by t.categoryId")
  List<CategoryRecentPostCount> countByCategorySince(@Param("since") Instant since);

  record AuthorPostCount(Long authorId, long posts) {}

  /**
   * Who wrote the most visible posts since {@code since}, most first. Deleted and banned accounts
   * are left out (section 8.2); ties go to the older account.
   */
  @Query(
      "select new com.notathome.posts.PostRepository$AuthorPostCount(p.authorId, count(p))"
          + " from Post p join Topic t on p.topicId = t.id join User u on p.authorId = u.id"
          + " where t.deletedAt is null and p.deletedAt is null and p.createdAt >= :since"
          + " and u.deletedAt is null and u.status = com.notathome.users.UserStatus.ACTIVE"
          + " group by p.authorId order by count(p) desc, p.authorId asc")
  List<AuthorPostCount> mostActiveAuthorsSince(@Param("since") Instant since, Pageable pageable);

  /** Visible posts of one author in active topics, for their profile (FR-8.1). */
  @Query(
      "select count(p) from Post p join Topic t on p.topicId = t.id"
          + " where p.authorId = :authorId and p.deletedAt is null and t.deletedAt is null")
  long countVisibleByAuthor(@Param("authorId") Long authorId);

  /** The author's newest visible posts in active topics, newest first (FR-8.1). */
  @Query(
      "select p from Post p join Topic t on p.topicId = t.id"
          + " where p.authorId = :authorId and p.deletedAt is null and t.deletedAt is null"
          + " order by p.id desc")
  List<Post> recentVisibleByAuthor(@Param("authorId") Long authorId, Pageable pageable);

  record LatestPost(Long topicId, Long postId) {}

  /** For each topic, its newest post that has not been removed (FR-7.2). */
  @Query(
      "select new com.notathome.posts.PostRepository$LatestPost(p.topicId, max(p.id))"
          + " from Post p where p.topicId in :topicIds and p.deletedAt is null group by p.topicId")
  List<LatestPost> latestVisiblePosts(@Param("topicIds") Collection<Long> topicIds);
}

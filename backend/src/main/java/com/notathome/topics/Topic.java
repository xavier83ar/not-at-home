package com.notathome.topics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "topics")
public class Topic {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long categoryId;
  private Long authorId;
  private String title;

  /** Cosmetic part of the URL; lookups use the id. Set when the topic is created. */
  private String slug;

  @Column(name = "is_pinned")
  private boolean pinned;

  @Column(name = "is_locked")
  private boolean locked;

  private Instant lastPostAt;
  private int replyCount;

  @Column(updatable = false)
  private Instant createdAt;

  private Instant deletedAt;

  protected Topic() {}

  public Topic(Long categoryId, Long authorId, String title, String slug, Instant createdAt) {
    this.categoryId = categoryId;
    this.authorId = authorId;
    this.title = title;
    this.slug = slug;
    this.createdAt = createdAt;
    this.lastPostAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public Long getCategoryId() {
    return categoryId;
  }

  public Long getAuthorId() {
    return authorId;
  }

  public String getTitle() {
    return title;
  }

  public String getSlug() {
    return slug;
  }

  public boolean isPinned() {
    return pinned;
  }

  public boolean isLocked() {
    return locked;
  }

  public Instant getLastPostAt() {
    return lastPostAt;
  }

  public int getReplyCount() {
    return replyCount;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  /** FR-5.5. Callers remove the topic's posts with it ({@code PostRepository.removeAllOfTopic}). */
  public void remove(Instant at) {
    this.deletedAt = at;
  }

  /** Called by {@link TopicCounters}, the only place these two values are worked out. */
  void updateActivity(int replyCount, Instant lastPostAt) {
    this.replyCount = replyCount;
    this.lastPostAt = lastPostAt;
  }
}

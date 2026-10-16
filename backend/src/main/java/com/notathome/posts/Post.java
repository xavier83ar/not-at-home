package com.notathome.posts;

import com.notathome.content.ProcessedContent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "posts")
public class Post {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long topicId;
  private Long authorId;

  /** Editor.js JSON, already validated and sanitized (see {@code EditorContentProcessor}). */
  @JdbcTypeCode(SqlTypes.JSON)
  private String content;

  private String searchText;
  private Instant editedAt;

  @Column(updatable = false)
  private Instant createdAt;

  private Instant deletedAt;

  protected Post() {}

  public Post(Long topicId, Long authorId, ProcessedContent content, Instant createdAt) {
    this.topicId = topicId;
    this.authorId = authorId;
    this.content = content.json();
    this.searchText = content.searchText();
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public Long getTopicId() {
    return topicId;
  }

  public Long getAuthorId() {
    return authorId;
  }

  public String getContent() {
    return content;
  }

  public String getSearchText() {
    return searchText;
  }

  public Instant getEditedAt() {
    return editedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  /** FR-5.1: replaces the content and marks the post as edited. */
  void edit(ProcessedContent content, Instant at) {
    this.content = content.json();
    this.searchText = content.searchText();
    this.editedAt = at;
  }

  /** FR-5.5: the row stays; every list and count skips it from now on. */
  void remove(Instant at) {
    this.deletedAt = at;
  }
}

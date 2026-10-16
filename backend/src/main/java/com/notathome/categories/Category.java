package com.notathome.categories;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "categories")
public class Category {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String name;

  /** Fixed once created (FR-2.1): changing it would break every link already out there. */
  private String slug;

  private String description;
  private Long coverUploadId;
  private int position;

  @Column(insertable = false, updatable = false)
  private Instant createdAt;

  private Instant deletedAt;

  protected Category() {}

  Category(String name, String slug, String description, Long coverUploadId, int position) {
    this.name = name;
    this.slug = slug;
    this.description = description;
    this.coverUploadId = coverUploadId;
    this.position = position;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  void rename(String name) {
    this.name = name;
  }

  public String getSlug() {
    return slug;
  }

  public String getDescription() {
    return description;
  }

  void changeDescription(String description) {
    this.description = description;
  }

  public Long getCoverUploadId() {
    return coverUploadId;
  }

  void changeCover(Long coverUploadId) {
    this.coverUploadId = coverUploadId;
  }

  public int getPosition() {
    return position;
  }

  void reorderTo(int position) {
    this.position = position;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  void softDelete(Instant deletedAt) {
    this.deletedAt = deletedAt;
  }
}

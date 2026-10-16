package com.notathome.uploads;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "uploads")
public class Upload {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long ownerId;
  private String storageKey;
  private String contentType;
  private long sizeBytes;

  @Column(insertable = false, updatable = false)
  private Instant createdAt;

  protected Upload() {}

  public Upload(Long ownerId, String storageKey, String contentType, long sizeBytes) {
    this.ownerId = ownerId;
    this.storageKey = storageKey;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
  }

  public Long getId() {
    return id;
  }

  public Long getOwnerId() {
    return ownerId;
  }

  /** The random file name on disk; also what the public URL is built from. */
  public String getStorageKey() {
    return storageKey;
  }

  public String getContentType() {
    return contentType;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}

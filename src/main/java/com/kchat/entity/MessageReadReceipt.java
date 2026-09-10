package com.kchat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "message_read_receipts")
@IdClass(MessageReadReceipt.Pk.class)
public class MessageReadReceipt {

  @Id
  @Column(name = "message_id", nullable = false)
  private UUID messageId;

  @Id
  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "read_at", nullable = false)
  private Instant readAt;

  @PrePersist
  void onCreate() {
    if (readAt == null) {
      readAt = Instant.now();
    }
  }

  public UUID getMessageId() {
    return messageId;
  }

  public void setMessageId(UUID messageId) {
    this.messageId = messageId;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public Instant getReadAt() {
    return readAt;
  }

  public void setReadAt(Instant readAt) {
    this.readAt = readAt;
  }

  public static class Pk implements Serializable {
    private UUID messageId;
    private UUID userId;

    public Pk() {}

    public Pk(UUID messageId, UUID userId) {
      this.messageId = messageId;
      this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Pk pk)) {
        return false;
      }
      return Objects.equals(messageId, pk.messageId) && Objects.equals(userId, pk.userId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(messageId, userId);
    }
  }
}

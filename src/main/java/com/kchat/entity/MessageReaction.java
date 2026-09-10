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
@Table(name = "message_reactions")
@IdClass(MessageReaction.Pk.class)
public class MessageReaction {

  @Id
  @Column(name = "message_id", nullable = false)
  private UUID messageId;

  @Id
  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Id
  @Column(name = "emoji", nullable = false, length = 32)
  private String emoji;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
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

  public String getEmoji() {
    return emoji;
  }

  public void setEmoji(String emoji) {
    this.emoji = emoji;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public static class Pk implements Serializable {
    private UUID messageId;
    private UUID userId;
    private String emoji;

    public Pk() {}

    public Pk(UUID messageId, UUID userId, String emoji) {
      this.messageId = messageId;
      this.userId = userId;
      this.emoji = emoji;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Pk pk)) {
        return false;
      }
      return Objects.equals(messageId, pk.messageId)
          && Objects.equals(userId, pk.userId)
          && Objects.equals(emoji, pk.emoji);
    }

    @Override
    public int hashCode() {
      return Objects.hash(messageId, userId, emoji);
    }
  }
}

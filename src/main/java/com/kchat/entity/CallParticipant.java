package com.kchat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "call_participants")
@IdClass(CallParticipant.Pk.class)
public class CallParticipant {

  @Id
  @Column(name = "call_id", nullable = false)
  private UUID callId;

  @Id
  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "call_id", insertable = false, updatable = false)
  private CallSession call;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", insertable = false, updatable = false)
  private User user;

  @Column(name = "joined_at")
  private Instant joinedAt;

  @Column(name = "left_at")
  private Instant leftAt;

  public UUID getCallId() {
    return callId;
  }

  public void setCallId(UUID callId) {
    this.callId = callId;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public Instant getJoinedAt() {
    return joinedAt;
  }

  public void setJoinedAt(Instant joinedAt) {
    this.joinedAt = joinedAt;
  }

  public Instant getLeftAt() {
    return leftAt;
  }

  public void setLeftAt(Instant leftAt) {
    this.leftAt = leftAt;
  }

  public static class Pk implements Serializable {
    private UUID callId;
    private UUID userId;

    public Pk() {}

    public Pk(UUID callId, UUID userId) {
      this.callId = callId;
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
      return Objects.equals(callId, pk.callId) && Objects.equals(userId, pk.userId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(callId, userId);
    }
  }
}

package com.kchat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "user_contacts")
@IdClass(UserContact.Pk.class)
public class UserContact implements Persistable<UserContact.Pk> {

  @Id
  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Id
  @Column(name = "contact_user_id", nullable = false)
  private UUID contactUserId;

  @Transient private boolean newEntity = true;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "owner_id", insertable = false, updatable = false)
  private User owner;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "contact_user_id", insertable = false, updatable = false)
  private User contactUser;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  @PostLoad
  @PostPersist
  void markPersisted() {
    newEntity = false;
  }

  @Override
  public Pk getId() {
    return new Pk(ownerId, contactUserId);
  }

  @Override
  public boolean isNew() {
    return newEntity;
  }

  public UUID getOwnerId() {
    return ownerId;
  }

  public void setOwnerId(UUID ownerId) {
    this.ownerId = ownerId;
  }

  public UUID getContactUserId() {
    return contactUserId;
  }

  public void setContactUserId(UUID contactUserId) {
    this.contactUserId = contactUserId;
  }

  public User getOwner() {
    return owner;
  }

  public void setOwner(User owner) {
    this.owner = owner;
    if (owner != null) {
      this.ownerId = owner.getId();
    }
  }

  public User getContactUser() {
    return contactUser;
  }

  public void setContactUser(User contactUser) {
    this.contactUser = contactUser;
    if (contactUser != null) {
      this.contactUserId = contactUser.getId();
    }
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public static class Pk implements Serializable {
    private UUID ownerId;
    private UUID contactUserId;

    public Pk() {}

    public Pk(UUID ownerId, UUID contactUserId) {
      this.ownerId = ownerId;
      this.contactUserId = contactUserId;
    }

    public UUID getOwnerId() {
      return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
      this.ownerId = ownerId;
    }

    public UUID getContactUserId() {
      return contactUserId;
    }

    public void setContactUserId(UUID contactUserId) {
      this.contactUserId = contactUserId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Pk pk)) {
        return false;
      }
      return Objects.equals(ownerId, pk.ownerId) && Objects.equals(contactUserId, pk.contactUserId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(ownerId, contactUserId);
    }
  }
}

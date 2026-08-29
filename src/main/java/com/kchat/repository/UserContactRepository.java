package com.kchat.repository;

import com.kchat.entity.User;
import com.kchat.entity.UserContact;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserContactRepository extends JpaRepository<UserContact, UserContact.Pk> {

    boolean existsByOwnerIdAndContactUserId(UUID ownerId, UUID contactUserId);

    void deleteByOwnerIdAndContactUserId(UUID ownerId, UUID contactUserId);

    @Query("""
            SELECT c.contactUser FROM UserContact c
            WHERE c.ownerId = :ownerId
              AND c.contactUser.status = com.kchat.common.enums.UserStatus.active
            ORDER BY LOWER(c.contactUser.displayName) ASC
            """)
    List<User> findActiveContactUsers(@Param("ownerId") UUID ownerId);

    @Query("""
            SELECT c.contactUserId FROM UserContact c
            WHERE c.ownerId = :ownerId
              AND c.contactUserId IN :ids
            """)
    List<UUID> findContactUserIdsAmong(
            @Param("ownerId") UUID ownerId,
            @Param("ids") Collection<UUID> ids
    );
}

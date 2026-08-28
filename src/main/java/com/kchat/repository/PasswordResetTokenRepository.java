package com.kchat.repository;

import com.kchat.entity.PasswordResetToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    @Query("""
            SELECT t from PasswordResetToken t
            JOIN FETCH t.user
            WHERE t.resetTokenHash = :resetTokenHash
            """)
    Optional<PasswordResetToken> findByResetTokenHashWithUser(@Param("resetTokenHash") String resetTokenHash);

    Optional<PasswordResetToken> findFirstByUser_IdAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            UUID userId,
            Instant expiresAfter
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PasswordResetToken t
            SET t.usedAt = CURRENT_TIMESTAMP
            WHERE t.user.id = :userId AND t.usedAt IS NULL
            """)
    void markAllUsedForUser(@Param("userId") UUID userId);
}

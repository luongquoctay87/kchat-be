package com.kchat.repository;

import com.kchat.entity.MessageReadReceipt;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageReadReceiptRepository extends JpaRepository<MessageReadReceipt, MessageReadReceipt.Pk> {

    interface ReceiptRow {
        UUID getUserId();

        String getDisplayName();

        Instant getReadAt();
    }

    @Query("""
            SELECT r.userId AS userId, u.displayName AS displayName, r.readAt AS readAt
            FROM MessageReadReceipt r
            JOIN User u ON u.id = r.userId
            WHERE r.messageId = :messageId
            ORDER BY r.readAt ASC
            """)
    List<ReceiptRow> findRowsByMessageId(@Param("messageId") UUID messageId);

    @Modifying(clearAutomatically = false, flushAutomatically = true)
    @Query(value = """
            INSERT INTO message_read_receipts (message_id, user_id, read_at)
            SELECT m.id, :userId, :readAt
            FROM messages m
            WHERE m.room_id = :roomId
              AND m.deleted_at IS NULL
              AND m.sender_id IS NOT NULL
              AND m.sender_id <> :userId
              AND m.created_at > :afterCreatedAt
              AND m.created_at <= :upToCreatedAt
            ON CONFLICT (message_id, user_id) DO NOTHING
            """, nativeQuery = true)
    int insertReceiptsUpTo(
            @Param("roomId") UUID roomId,
            @Param("userId") UUID userId,
            @Param("upToCreatedAt") Instant upToCreatedAt,
            @Param("afterCreatedAt") Instant afterCreatedAt,
            @Param("readAt") Instant readAt
    );
}

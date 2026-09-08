package com.kchat.repository;

import com.kchat.entity.ChatMessage;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    @Query("""
            SELECT m FROM ChatMessage m
            JOIN FETCH m.room
            LEFT JOIN FETCH m.sender
            LEFT JOIN FETCH m.replyTo rt
            LEFT JOIN FETCH rt.sender
            WHERE m.room.id = :roomId AND m.deletedAt IS NULL
            ORDER BY m.createdAt DESC
            """)
    List<ChatMessage> findRecentByRoomId(@Param("roomId") UUID roomId, Pageable pageable);

    @Query(value = """
            SELECT DISTINCT ON (m.room_id) m.id
            FROM messages m
            WHERE m.room_id IN (:roomIds) AND m.deleted_at IS NULL
            ORDER BY m.room_id, m.created_at DESC
            """, nativeQuery = true)
    List<UUID> findLatestMessageIdsByRoomIds(@Param("roomIds") Collection<UUID> roomIds);

    @Query("""
            SELECT m FROM ChatMessage m
            LEFT JOIN FETCH m.sender
            JOIN FETCH m.room
            WHERE m.id IN :ids
            """)
    List<ChatMessage> findAllWithSenderByIdIn(@Param("ids") Collection<UUID> ids);

    @Query("""
            SELECT m FROM ChatMessage m
            LEFT JOIN FETCH m.sender
            JOIN FETCH m.room
            LEFT JOIN FETCH m.replyTo rt
            LEFT JOIN FETCH rt.sender
            WHERE m.id = :id AND m.deletedAt IS NULL
            """)
    Optional<ChatMessage> findActiveById(@Param("id") UUID id);

    @Query("""
            SELECT CASE WHEN COUNT(m) > 0 THEN TRUE ELSE FALSE END
            FROM ChatMessage m
            WHERE m.id = :id AND m.room.id = :roomId AND m.deletedAt IS NULL
            """)
    boolean existsActiveInRoom(@Param("id") UUID id, @Param("roomId") UUID roomId);

    @Query("""
            SELECT DISTINCT m FROM ChatMessage m
            LEFT JOIN FETCH m.sender
            JOIN FETCH m.room
            LEFT JOIN MessageAttachment a ON a.message.id = m.id
            WHERE m.room.id = :roomId
            AND m.deletedAt IS NULL
            AND (
                LOWER(COALESCE(m.content, '')) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '!'
                OR LOWER(COALESCE(a.fileName, '')) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '!'
            )
            ORDER BY m.createdAt DESC
            """)
    List<ChatMessage> searchByRoomIdAndContent(
            @Param("roomId") UUID roomId,
            @Param("query") String query,
            Pageable pageable
    );

    @Query(value = """
            SELECT m.id AS id, m.room_id AS room_id
            FROM messages m
            JOIN rooms r ON r.id = m.room_id
            WHERE r.disappearing_after_seconds IS NOT NULL
              AND r.disappearing_after_seconds > 0
              AND m.created_at < NOW() - (r.disappearing_after_seconds || ' seconds')::INTERVAL
              AND NOT EXISTS (
                  SELECT 1 FROM pinned_messages p WHERE p.message_id = m.id
              )
            ORDER BY m.created_at
            LIMIT :limit
            """, nativeQuery = true)
    List<ExpiredMessageRow> findExpiredForDisappearingCleanup(@Param("limit") int limit);

    @Query(value = """
            SELECT m.id AS id, m.room_id AS room_id
            FROM messages m
            WHERE m.sender_id = :senderId AND m.deleted_at IS NULL
            ORDER BY m.created_at
            LIMIT :limit
            """, nativeQuery = true)
    List<SenderMessageRow> findActiveIdsBySenderId(@Param("senderId") UUID senderId, @Param("limit") int limit);

    @Query(value = """
            SELECT m.id AS id, m.room_id AS room_id
            FROM messages m
            WHERE m.room_id IN (:roomIds) AND m.deleted_at IS NULL
            ORDER BY m.created_at
            LIMIT :limit
            """, nativeQuery = true)
    List<SenderMessageRow> findActiveIdsByRoomIdIn(@Param("roomIds") Collection<UUID> roomIds, @Param("limit") int limit);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ChatMessage m SET m.deletedAt = :deletedAt WHERE m.id IN :ids AND m.deletedAt IS NULL")
    int softDeleteByIdIn(@Param("ids") Collection<UUID> ids, @Param("deletedAt") Instant deletedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ChatMessage m WHERE m.id IN :ids")
    int deleteByIdIn(@Param("ids") Collection<UUID> ids);
}

package com.kchat.repository;

import com.kchat.entity.PinnedMessage;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PinnedMessageRepository extends JpaRepository<PinnedMessage, PinnedMessage.Pk> {

  @Query(
      """
            SELECT p FROM PinnedMessage p
            JOIN FETCH p.message m
            LEFT JOIN FETCH m.sender
            WHERE p.roomId = :roomId
            ORDER BY p.pinnedAt DESC
            """)
  List<PinnedMessage> findByRoomIdOrderByPinnedAtDesc(@Param("roomId") UUID roomId);

  long countByRoomId(UUID roomId);

  boolean existsByRoomIdAndMessageId(UUID roomId, UUID messageId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM PinnedMessage p WHERE p.roomId = :roomId AND p.messageId = :messageId")
  int deleteByRoomIdAndMessageId(@Param("roomId") UUID roomId, @Param("messageId") UUID messageId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM PinnedMessage p WHERE p.messageId IN :messageIds")
  int deleteByMessageIdIn(@Param("messageIds") Collection<UUID> messageIds);
}

package com.kchat.repository;

import com.kchat.entity.ChannelWebhook;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChannelWebhookRepository extends JpaRepository<ChannelWebhook, UUID> {

  @Query(
      """
            SELECT w FROM ChannelWebhook w
            JOIN FETCH w.room
            WHERE w.id = :id
            """)
  Optional<ChannelWebhook> findByIdWithRoom(@Param("id") UUID id);

  @Query(
      """
            SELECT w FROM ChannelWebhook w
            WHERE w.room.id = :roomId
            ORDER BY w.createdAt DESC
            """)
  List<ChannelWebhook> findByRoomIdOrderByCreatedAtDesc(@Param("roomId") UUID roomId);

  @Query(
      """
            SELECT w FROM ChannelWebhook w
            JOIN FETCH w.room r
            WHERE r.slug = :slug AND LOWER(w.name) = LOWER(:name)
            """)
  Optional<ChannelWebhook> findByRoomSlugAndName(
      @Param("slug") String slug, @Param("name") String name);
}

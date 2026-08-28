package com.kchat.repository;

import com.kchat.entity.MessageAttachment;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, UUID> {

    @Query("""
            SELECT a FROM MessageAttachment a
            JOIN FETCH a.message m
            JOIN FETCH m.room
            WHERE a.id = :id
            """)
    java.util.Optional<MessageAttachment> findByIdWithMessageAndRoom(@Param("id") UUID id);

    @Query("""
            SELECT a FROM MessageAttachment a
            JOIN FETCH a.message
            WHERE a.message.id IN :messageIds
            ORDER BY a.createdAt ASC
            """)
    List<MessageAttachment> findByMessageIdIn(@Param("messageIds") Collection<UUID> messageIds);
}

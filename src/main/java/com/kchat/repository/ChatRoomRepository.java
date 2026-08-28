package com.kchat.repository;

import com.kchat.common.enums.RoomType;
import com.kchat.entity.ChatRoom;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {

    Optional<ChatRoom> findBySlug(String slug);

    List<ChatRoom> findByTypeAndArchivedFalse(RoomType type);
}

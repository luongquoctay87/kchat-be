package com.kchat.ws;

import com.kchat.common.dto.chat.MessageDto;
import java.util.List;
import java.util.UUID;

public interface MessageEventPublisher {

    void messageCreated(UUID roomId, UUID senderId, List<UUID> memberIds, MessageDto message);

    void messageUpdated(UUID roomId, UUID senderId, List<UUID> memberIds, MessageDto message);

    void messageDeleted(UUID roomId, String messageId, List<UUID> memberIds);

    void messagesRead(UUID roomId, UUID readerId, String upToMessageId, long upToCreatedAt, List<UUID> memberIds);
}

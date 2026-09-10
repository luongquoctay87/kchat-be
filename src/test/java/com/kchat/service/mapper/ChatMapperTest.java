package com.kchat.service.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kchat.common.enums.MessageType;
import com.kchat.common.enums.RoomType;
import com.kchat.entity.ChatMessage;
import com.kchat.entity.User;
import org.junit.jupiter.api.Test;

class ChatMapperTest {

  @Test
  void groupPreviewPrefixesSender() {
    User sender = new User();
    sender.setDisplayName("Nguyễn Văn A");
    ChatMessage message = new ChatMessage();
    message.setType(MessageType.text);
    message.setContent("deploy xong rồi");
    message.setSender(sender);

    assertEquals("A: deploy xong rồi", ChatMapper.previewText(message, RoomType.group));
  }

  @Test
  void systemPreviewAddsBotPrefix() {
    ChatMessage message = new ChatMessage();
    message.setType(MessageType.system);
    message.setContent("⚠ Deploy failed · Service: k-chat-api");
    assertEquals("[Bot] ⚠ Deploy failed", ChatMapper.previewText(message, RoomType.channel));
  }
}

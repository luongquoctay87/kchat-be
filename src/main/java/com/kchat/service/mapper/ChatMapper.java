package com.kchat.service.mapper;

import com.kchat.common.dto.chat.MessageDto;
import com.kchat.common.dto.chat.ReactionDto;
import com.kchat.common.enums.MessageType;
import com.kchat.common.enums.RoomType;
import com.kchat.common.util.BotAlertFormat;
import com.kchat.common.util.ChatTimeFormat;
import com.kchat.entity.ChatMessage;
import com.kchat.entity.MessageAttachment;
import com.kchat.entity.User;
import java.util.List;
import java.util.UUID;

public final class ChatMapper {

  private ChatMapper() {}

  public static MessageDto toMessageDto(
      ChatMessage message, UUID me, MessageAttachment attachment, boolean isRead) {
    return toMessageDto(message, me, attachment, isRead, List.of());
  }

  public static MessageDto toMessageDto(
      ChatMessage message,
      UUID me,
      MessageAttachment attachment,
      boolean isRead,
      List<ReactionDto> reactions) {
    return toMessageDto(message, me, attachment, isRead, reactions, null);
  }

  public static MessageDto toMessageDto(
      ChatMessage message,
      UUID me,
      MessageAttachment attachment,
      boolean isRead,
      List<ReactionDto> reactions,
      MessageAttachment replyAttachment) {
    boolean mine =
        message.getSender() != null && me != null && me.equals(message.getSender().getId());
    MessageType type = message.getType() != null ? message.getType() : MessageType.text;
    String text = message.getContent() != null ? message.getContent() : "";

    String fileName = null;
    String fileSize = null;
    String imageLabel = null;
    String mediaUrl = null;
    if (attachment != null) {
      fileName = attachment.getFileName();
      fileSize = ChatTimeFormat.formatFileSize(attachment.getFileSize());
      mediaUrl = "/media/" + attachment.getId();
      if (type == MessageType.image) {
        imageLabel = attachment.getFileName();
      }
    }

    String senderName = null;
    if (!mine && message.getSender() != null) {
      senderName = message.getSender().getDisplayName();
    }

    String replyAuthor = null;
    String replyText = null;
    String replyToId = null;
    String replyMediaUrl = null;
    String replyType = null;
    ChatMessage reply = message.getReplyTo();
    if (reply != null && !reply.isDeleted()) {
      replyToId = reply.getId().toString();
      replyAuthor = reply.getSender() != null ? reply.getSender().getDisplayName() : "System";
      MessageType quotedType = reply.getType() != null ? reply.getType() : MessageType.text;
      replyType = quotedType.name();
      String replyContent = reply.getContent() != null ? reply.getContent() : "";
      replyText =
          switch (quotedType) {
            case image -> replyContent.isBlank() ? "Ảnh" : replyContent;
            case file ->
                replyAttachment != null && replyAttachment.getFileName() != null
                    ? replyAttachment.getFileName()
                    : "[File]";
            default -> replyContent;
          };
      if (quotedType == MessageType.image && replyAttachment != null) {
        replyMediaUrl = "/media/" + replyAttachment.getId();
      }
    }

    String botTitle = null;
    String botService = null;
    if (type == MessageType.system) {
      var parsed = BotAlertFormat.parse(text);
      botTitle = parsed.title();
      botService = parsed.service();
    }

    return new MessageDto(
        message.getId().toString(),
        type.name(),
        text,
        fileName,
        fileSize,
        imageLabel,
        mediaUrl,
        senderName,
        mine,
        ChatTimeFormat.formatMessageClock(message.getCreatedAt()),
        message.getCreatedAt() != null ? message.getCreatedAt().toEpochMilli() : null,
        replyAuthor,
        replyText,
        replyToId,
        replyMediaUrl,
        replyType,
        isRead,
        message.getEditedAt() != null,
        botTitle,
        botService,
        reactions != null ? reactions : List.of());
  }

  public static String previewText(ChatMessage message, RoomType roomType) {
    if (message == null) {
      return "";
    }
    MessageType type = message.getType() != null ? message.getType() : MessageType.text;
    String body =
        switch (type) {
          case image -> "[Ảnh]";
          case file -> "[File]";
          case call_event -> {
            String c = message.getContent() != null ? message.getContent().trim() : "";
            yield c.isBlank() ? "[Cuộc gọi]" : c;
          }
          case system -> {
            String c = message.getContent() != null ? message.getContent() : "";
            if (c.isBlank()) {
              yield "[Bot]";
            }
            yield "[Bot] " + BotAlertFormat.parse(c).title();
          }
          case text -> message.getContent() != null ? message.getContent() : "";
        };

    if (roomType == RoomType.group && message.getSender() != null && type == MessageType.text) {
      String name = shortName(message.getSender());
      return name + ": " + truncate(body, 80);
    }
    return truncate(body, 100);
  }

  private static String shortName(User user) {
    String name = user.getDisplayName();
    if (name == null || name.isBlank()) {
      return user.loginName();
    }
    String[] parts = name.trim().split("\\s+");
    return parts[parts.length - 1];
  }

  private static String truncate(String value, int max) {
    if (value == null) {
      return "";
    }
    String trimmed = value.trim();
    if (trimmed.length() <= max) {
      return trimmed;
    }
    return trimmed.substring(0, max - 1) + "…";
  }
}

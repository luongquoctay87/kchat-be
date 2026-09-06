package com.kchat.common.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record MessageDto(
        String id,
        String type,
        String text,
        String fileName,
        String fileSize,
        String imageLabel,
        String mediaUrl,
        String senderName,
        @JsonProperty("is_mine") boolean isMine,
        String time,
        Long createdAt,
        String replyAuthor,
        String replyText,
        String replyToId,
        String replyMediaUrl,
        String replyType,
        boolean isRead,
        boolean edited,
        String botTitle,
        String botService,
        List<ReactionDto> reactions
) {
}

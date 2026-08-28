package com.kchat.ws;

import com.kchat.common.dto.chat.MessageDto;
import com.kchat.common.dto.chat.ReactionDto;
import java.util.List;

final class MessageDtoCopy {

    private MessageDtoCopy() {
    }

    static MessageDto withMine(MessageDto source, boolean isMine) {
        // Strip reacted_by_me for fan-out — each client merges from local state.
        List<ReactionDto> reactions = source.reactions() == null
                ? List.of()
                : source.reactions().stream()
                        .map(r -> new ReactionDto(r.emoji(), r.count(), false))
                        .toList();
        return new MessageDto(
                source.id(),
                source.type(),
                source.text(),
                source.fileName(),
                source.fileSize(),
                source.imageLabel(),
                source.mediaUrl(),
                isMine ? null : source.senderName(),
                isMine,
                source.time(),
                source.createdAt(),
                source.replyAuthor(),
                source.replyText(),
                source.replyToId(),
                source.replyMediaUrl(),
                source.replyType(),
                source.isRead(),
                source.edited(),
                source.botTitle(),
                source.botService(),
                reactions
        );
    }
}

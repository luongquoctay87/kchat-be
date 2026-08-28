package com.kchat.repository;

import com.kchat.entity.MessageReaction;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageReactionRepository extends JpaRepository<MessageReaction, MessageReaction.Pk> {

    List<MessageReaction> findByMessageIdIn(Collection<UUID> messageIds);

    List<MessageReaction> findByMessageId(UUID messageId);

    Optional<MessageReaction> findByMessageIdAndUserIdAndEmoji(UUID messageId, UUID userId, String emoji);
}

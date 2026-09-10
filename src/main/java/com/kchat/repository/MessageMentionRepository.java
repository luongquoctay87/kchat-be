package com.kchat.repository;

import com.kchat.entity.MessageMention;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageMentionRepository extends JpaRepository<MessageMention, MessageMention.Pk> {

  @Modifying(clearAutomatically = false, flushAutomatically = true)
  @Query("DELETE FROM MessageMention m WHERE m.messageId = :messageId")
  void deleteByMessageId(@Param("messageId") UUID messageId);
}

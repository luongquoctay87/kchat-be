package com.kchat.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.kchat.common.dto.chat.MessageDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class MessageDtoCopyTest {

  @Test
  void withMineClearsSenderNameForSelf() {
    MessageDto source =
        new MessageDto(
            "id", "text", "hi", null, null, null, null, "Alice", false, "10:00", 1L, null, null,
            null, null, null, false, false, null, null, List.of());
    MessageDto mine = MessageDtoCopy.withMine(source, true);
    assertTrue(mine.isMine());
    assertNull(mine.senderName());
    assertEquals("hi", mine.text());

    MessageDto theirs = MessageDtoCopy.withMine(source, false);
    assertFalse(theirs.isMine());
    assertEquals("Alice", theirs.senderName());
  }
}

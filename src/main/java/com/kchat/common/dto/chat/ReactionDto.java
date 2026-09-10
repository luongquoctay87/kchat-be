package com.kchat.common.dto.chat;

public record ReactionDto(String emoji, int count, boolean reactedByMe) {
  public ReactionDto(String emoji, int count) {
    this(emoji, count, false);
  }
}

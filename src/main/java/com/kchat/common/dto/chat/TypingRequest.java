package com.kchat.common.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TypingRequest(@JsonProperty("typing") boolean typing) {}

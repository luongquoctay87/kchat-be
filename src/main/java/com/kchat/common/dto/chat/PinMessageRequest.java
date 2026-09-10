package com.kchat.common.dto.chat;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PinMessageRequest(@NotNull UUID messageId) {}

package com.kchat.common.dto.chat;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record AddMembersRequest(@NotEmpty @Size(max = 50) List<UUID> userIds) {}

package com.kchat.common.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateGroupRequest(
        @NotBlank @Size(max = 128) String name,
        @NotEmpty @Size(max = 49) List<UUID> memberIds
) {
}

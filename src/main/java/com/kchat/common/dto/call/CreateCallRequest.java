package com.kchat.common.dto.call;

import jakarta.validation.constraints.NotBlank;

public record CreateCallRequest(
        @NotBlank String callType
) {
}

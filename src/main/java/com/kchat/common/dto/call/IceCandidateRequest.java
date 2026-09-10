package com.kchat.common.dto.call;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record IceCandidateRequest(
    @NotBlank String candidate,
    @JsonProperty("sdp_mid") String sdpMid,
    @JsonProperty("sdp_m_line_index") Integer sdpMLineIndex) {}

package com.kchat.common.dto.call;

import java.util.List;

public record IceServersResponse(List<IceServerDto> iceServers, Long ttlSeconds) {}

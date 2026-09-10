package com.kchat.common.dto.call;

import java.util.List;

public record IceServerDto(List<String> urls, String username, String credential) {}

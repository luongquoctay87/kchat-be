package com.kchat.service.impl;

import com.kchat.common.dto.call.IceServerDto;
import com.kchat.common.dto.call.IceServersResponse;
import com.kchat.config.WebRtcIceProperties;
import com.kchat.service.IceConfigService;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class IceConfigServiceImpl implements IceConfigService {

  private static final Logger log = LoggerFactory.getLogger(IceConfigServiceImpl.class);

  private static final List<String> DEV_OPENRELAY_URLS =
      List.of(
          "turn:openrelay.metered.ca:80",
          "turn:openrelay.metered.ca:443",
          "turn:openrelay.metered.ca:443?transport=tcp");

  private final WebRtcIceProperties properties;

  public IceConfigServiceImpl(WebRtcIceProperties properties) {
    this.properties = properties;
  }

  @Override
  public IceServersResponse iceServersForUser(UUID userId) {
    List<IceServerDto> servers = new ArrayList<>();
    Long ttl = null;

    List<String> stun = normalizeUrls(properties.getStunUrls());
    if (!stun.isEmpty()) {
      servers.add(new IceServerDto(stun, null, null));
    }

    List<String> turnUrls = normalizeUrls(properties.getTurnUrls());
    if (!turnUrls.isEmpty()) {
      if (StringUtils.hasText(properties.getTurnSharedSecret())) {
        long ttlSec = Math.max(60L, properties.getTurnCredentialTtlSeconds());
        long expiry = Instant.now().getEpochSecond() + ttlSec;
        String username = expiry + ":" + userId;
        String credential = hmacBase64(properties.getTurnSharedSecret(), username);
        servers.add(new IceServerDto(turnUrls, username, credential));
        ttl = ttlSec;
      } else if (StringUtils.hasText(properties.getTurnUsername())
          && StringUtils.hasText(properties.getTurnCredential())) {
        servers.add(
            new IceServerDto(
                turnUrls, properties.getTurnUsername(), properties.getTurnCredential()));
      } else {
        log.warn("kchat.webrtc.turn-urls set but no credential/shared-secret — TURN omitted");
      }
    } else if (properties.isAllowDevOpenrelay()) {
      log.debug("Using dev openrelay TURN fallback for user {}", userId);
      servers.add(new IceServerDto(DEV_OPENRELAY_URLS, "openrelayproject", "openrelayproject"));
    }

    if (properties.isIncludeOpenrelayFallback() && !turnUrls.isEmpty()) {
      log.debug("Appending openrelay TURN fallback for user {}", userId);
      servers.add(new IceServerDto(DEV_OPENRELAY_URLS, "openrelayproject", "openrelayproject"));
    }

    return new IceServersResponse(List.copyOf(servers), ttl);
  }

  private static List<String> normalizeUrls(List<String> raw) {
    if (raw == null || raw.isEmpty()) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    for (String entry : raw) {
      if (!StringUtils.hasText(entry)) {
        continue;
      }
      for (String part : entry.split(",")) {
        String url = part.trim();
        if (!url.isEmpty()) {
          out.add(url);
        }
      }
    }
    return List.copyOf(out);
  }

  private static String hmacBase64(String secret, String username) {
    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
      byte[] digest = mac.doFinal(username.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(digest);
    } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
      throw new IllegalStateException("Failed to mint TURN credential", ex);
    }
  }
}

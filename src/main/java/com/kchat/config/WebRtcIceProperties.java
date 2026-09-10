package com.kchat.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ICE / TURN config for WebRTC. Credentials stay on the server; clients call {@code GET
 * /calls/ice-servers}.
 *
 * <p>Production: set {@code turn-urls} + either static {@code turn-username}/ {@code
 * turn-credential} or coturn REST {@code turn-shared-secret}. Dev: if no TURN configured and {@code
 * allow-dev-openrelay=true}, falls back to Metered openrelay (emulator-friendly only — never enable
 * in prod).
 */
@ConfigurationProperties(prefix = "kchat.webrtc")
public class WebRtcIceProperties {

  /** Comma-separated or YAML list of STUN URLs. */
  private List<String> stunUrls =
      new ArrayList<>(List.of("stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302"));

  /** Comma-separated or YAML list of TURN URLs (turn: / turns:). */
  private List<String> turnUrls = new ArrayList<>();

  private String turnUsername = "";
  private String turnCredential = "";

  /**
   * Coturn REST API shared secret. When set (and turn-urls non-empty), issues time-limited
   * username/password instead of static credentials.
   */
  private String turnSharedSecret = "";

  /** TTL for ephemeral TURN credentials (seconds). */
  private long turnCredentialTtlSeconds = 86_400;

  /**
   * Dev-only fallback to public openrelay when no TURN is configured. Must stay false in
   * production.
   */
  private boolean allowDevOpenrelay = false;

  /**
   * Append Metered openrelay as an extra TURN server alongside configured TURN. Helps emulator QA
   * when coturn relay is unreachable; keep false in hardened prod.
   */
  private boolean includeOpenrelayFallback = false;

  public List<String> getStunUrls() {
    return stunUrls;
  }

  public void setStunUrls(List<String> stunUrls) {
    this.stunUrls = stunUrls != null ? stunUrls : new ArrayList<>();
  }

  public List<String> getTurnUrls() {
    return turnUrls;
  }

  public void setTurnUrls(List<String> turnUrls) {
    this.turnUrls = turnUrls != null ? turnUrls : new ArrayList<>();
  }

  public String getTurnUsername() {
    return turnUsername;
  }

  public void setTurnUsername(String turnUsername) {
    this.turnUsername = turnUsername != null ? turnUsername : "";
  }

  public String getTurnCredential() {
    return turnCredential;
  }

  public void setTurnCredential(String turnCredential) {
    this.turnCredential = turnCredential != null ? turnCredential : "";
  }

  public String getTurnSharedSecret() {
    return turnSharedSecret;
  }

  public void setTurnSharedSecret(String turnSharedSecret) {
    this.turnSharedSecret = turnSharedSecret != null ? turnSharedSecret : "";
  }

  public long getTurnCredentialTtlSeconds() {
    return turnCredentialTtlSeconds;
  }

  public void setTurnCredentialTtlSeconds(long turnCredentialTtlSeconds) {
    this.turnCredentialTtlSeconds = turnCredentialTtlSeconds;
  }

  public boolean isAllowDevOpenrelay() {
    return allowDevOpenrelay;
  }

  public void setAllowDevOpenrelay(boolean allowDevOpenrelay) {
    this.allowDevOpenrelay = allowDevOpenrelay;
  }

  public boolean isIncludeOpenrelayFallback() {
    return includeOpenrelayFallback;
  }

  public void setIncludeOpenrelayFallback(boolean includeOpenrelayFallback) {
    this.includeOpenrelayFallback = includeOpenrelayFallback;
  }
}

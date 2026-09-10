package com.kchat.common.util;

/** Format / parse system bot alerts stored in message content (#17). */
public final class BotAlertFormat {

  private static final String SERVICE_SEP = " · Service: ";

  private BotAlertFormat() {}

  public static String format(String title, String service, String text) {
    String t = title != null ? title.trim() : "";
    if (t.isBlank()) {
      t = "Bot alert";
    }
    StringBuilder sb = new StringBuilder(t);
    if (service != null && !service.isBlank()) {
      sb.append(SERVICE_SEP).append(service.trim());
    }
    if (text != null && !text.isBlank()) {
      sb.append('\n').append(text.trim());
    }
    return sb.toString();
  }

  public static Parsed parse(String content) {
    if (content == null || content.isBlank()) {
      return new Parsed("Bot", null);
    }
    String body = content.trim();
    String extra = null;
    int nl = body.indexOf('\n');
    if (nl >= 0) {
      extra = body.substring(nl + 1).trim();
      if (extra.isBlank()) {
        extra = null;
      }
      body = body.substring(0, nl).trim();
    }

    String title = body;
    String service = null;
    int sep = indexOfIgnoreCase(body, SERVICE_SEP);
    if (sep < 0) {
      // Legacy seed: "… · Service: …" with varying spaces around ·
      int legacy = indexOfIgnoreCase(body, "service:");
      if (legacy > 0) {
        title = body.substring(0, legacy).replaceAll("[·•]\\s*$", "").trim();
        service = body.substring(legacy + "service:".length()).trim();
        if (title.isBlank()) {
          title = "Bot";
        }
      }
    } else {
      title = body.substring(0, sep).trim();
      service = body.substring(sep + SERVICE_SEP.length()).trim();
    }

    if (service != null && service.isBlank()) {
      service = null;
    }
    if (service == null && extra != null && extra.toLowerCase().startsWith("service:")) {
      service = extra.substring("service:".length()).trim();
      extra = null;
    }
    if (service != null && extra != null) {
      service = service + " — " + extra;
    } else if (service == null && extra != null) {
      service = extra;
    }
    if (service != null && !service.isBlank() && !service.toLowerCase().startsWith("service:")) {
      service = "Service: " + service.trim();
    }
    if (title.isBlank()) {
      title = "Bot";
    }
    return new Parsed(title, service);
  }

  private static int indexOfIgnoreCase(String haystack, String needle) {
    return haystack.toLowerCase().indexOf(needle.toLowerCase());
  }

  public record Parsed(String title, String service) {}
}

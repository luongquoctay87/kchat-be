package com.kchat.media;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class MediaHttp {

  private MediaHttp() {}

  public static ResponseEntity<Resource> inline(MediaStorage.ObjectStream object) {
    return inline(object, null);
  }

  public static ResponseEntity<Resource> inline(
      MediaStorage.ObjectStream object, String cacheControl) {
    String fileName =
        object.fileName() != null && !object.fileName().isBlank() ? object.fileName() : "file";
    String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
    MediaType contentType;
    try {
      contentType = MediaType.parseMediaType(object.contentType());
    } catch (RuntimeException ex) {
      contentType = MediaType.APPLICATION_OCTET_STREAM;
    }
    ResponseEntity.BodyBuilder builder =
        ResponseEntity.ok()
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "inline; filename=\""
                    + fileName.replace("\"", "")
                    + "\"; filename*=UTF-8''"
                    + encoded)
            .contentType(contentType);
    if (cacheControl != null && !cacheControl.isBlank()) {
      builder.header(HttpHeaders.CACHE_CONTROL, cacheControl);
    }
    if (object.contentLength() >= 0) {
      builder.contentLength(object.contentLength());
    }
    return builder.body(new InputStreamResource(object.stream()));
  }
}

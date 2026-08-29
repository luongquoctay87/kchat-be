package com.kchat.media;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/** Object storage for chat attachments and profile avatars. */
public interface MediaStorage {

    record StoredObject(String key, long size, String contentType) {
    }

    record ObjectStream(InputStream stream, long contentLength, String contentType, String fileName)
            implements Closeable {
        @Override
        public void close() throws IOException {
            stream.close();
        }
    }

    StoredObject store(UUID roomId, MultipartFile file) throws IOException;

    StoredObject storeAvatar(UUID userId, MultipartFile file) throws IOException;

    StoredObject storeGroupAvatar(UUID roomId, MultipartFile file) throws IOException;

    ObjectStream open(String key) throws IOException;

    void deleteQuietly(String key);
}

package com.kchat.media;

import com.kchat.config.S3Properties;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
public class S3MediaStorage implements MediaStorage {

    private static final Logger log = LoggerFactory.getLogger(S3MediaStorage.class);

    private final S3Client s3Client;
    private final String bucket;

    public S3MediaStorage(S3Client s3Client, S3Properties properties) {
        this.s3Client = s3Client;
        this.bucket = properties.getBucket();
    }

    @Override
    public StoredObject store(UUID roomId, MultipartFile file) throws IOException {
        return put(MediaKeys.roomKey(roomId, file.getOriginalFilename()), file);
    }

    @Override
    public StoredObject storeAvatar(UUID userId, MultipartFile file) throws IOException {
        return put(MediaKeys.avatarKey(userId, file.getOriginalFilename()), file);
    }

    @Override
    public ObjectStream open(String key) throws IOException {
        MediaKeys.requireSafeKey(key);
        try {
            ResponseInputStream<GetObjectResponse> in = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            GetObjectResponse meta = in.response();
            String fileName = key.substring(key.lastIndexOf('/') + 1);
            String contentType = StringUtils.hasText(meta.contentType())
                    ? meta.contentType()
                    : "application/octet-stream";
            long length = meta.contentLength() != null ? meta.contentLength() : -1L;
            return new ObjectStream(in, length, contentType, fileName);
        } catch (NoSuchKeyException ex) {
            throw new IOException("Object not found", ex);
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new IOException("Object not found", ex);
            }
            throw new IOException("Failed to read object", ex);
        }
    }

    @Override
    public void deleteQuietly(String key) {
        if (!MediaKeys.isSafeKey(key)) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        } catch (RuntimeException ex) {
            log.debug("Could not delete S3 key {}: {}", key, ex.toString());
        }
    }

    private StoredObject put(String key, MultipartFile file) throws IOException {
        String contentType = StringUtils.hasText(file.getContentType())
                ? file.getContentType()
                : "application/octet-stream";
        long size = Math.max(file.getSize(), 0);
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();
        try (InputStream in = file.getInputStream()) {
            s3Client.putObject(request, RequestBody.fromInputStream(in, size));
        } catch (S3Exception ex) {
            throw new IOException("Failed to store object", ex);
        }
        return new StoredObject(key, size, contentType);
    }
}

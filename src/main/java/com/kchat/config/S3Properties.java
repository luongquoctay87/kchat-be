package com.kchat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kchat.s3")
public class S3Properties {

    private String bucket = "";
    private String region = "ap-southeast-1";
    /** S3-compatible endpoint (MinIO). Empty = AWS. */
    private String endpoint = "";
    /** Empty = default AWS credential chain (ECS task role). */
    private String accessKey = "";
    private String secretKey = "";

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket != null ? bucket.trim() : "";
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region != null && !region.isBlank() ? region.trim() : "ap-southeast-1";
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint != null ? endpoint.trim() : "";
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey != null ? accessKey.trim() : "";
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey != null ? secretKey.trim() : "";
    }
}

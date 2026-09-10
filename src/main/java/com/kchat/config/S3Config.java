package com.kchat.config;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
public class S3Config {

  @Bean(destroyMethod = "close")
  S3Client s3Client(S3Properties properties) {
    if (!StringUtils.hasText(properties.getBucket())) {
      throw new IllegalStateException("KCHAT_S3_BUCKET is required");
    }

    S3ClientBuilder builder = S3Client.builder().region(Region.of(properties.getRegion()));

    if (StringUtils.hasText(properties.getAccessKey())) {
      builder.credentialsProvider(
          StaticCredentialsProvider.create(
              AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())));
    } else {
      builder.credentialsProvider(DefaultCredentialsProvider.create());
    }

    if (StringUtils.hasText(properties.getEndpoint())) {
      builder
          .endpointOverride(URI.create(properties.getEndpoint()))
          .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
    }

    return builder.build();
  }
}

package com.ticket_system.manage_revenue_ticket.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.time.Duration;

/**
 * S3 client pointed at MinIO. Plain S3 API on purpose: switching to another S3-compatible store
 * (Garage, SeaweedFS) only changes minio.url and the keys.
 */
@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class S3StorageConfig {

    private static final Logger log = LoggerFactory.getLogger(S3StorageConfig.class);

    @Bean
    S3Client s3Client(MinioProperties properties) {
        return buildClient(properties);
    }

    /** Building the client does not connect, so the app still starts when MinIO is down or keys are unset. */
    public static S3Client buildClient(MinioProperties properties) {
        return S3Client.builder()
                .endpointOverride(URI.create(properties.url()))
                .region(Region.US_EAST_1)              // MinIO ignores the region, the SDK requires one
                .forcePathStyle(true)                  // http://host:9000/bucket/key, no bucket subdomain
                .credentialsProvider(credentials(properties))
                .httpClientBuilder(UrlConnectionHttpClient.builder()    // JDK HTTP client, see pom.xml
                        .connectionTimeout(Duration.ofSeconds(2))
                        .socketTimeout(Duration.ofSeconds(5)))
                // A hung MinIO must not hold a request thread: whole call (retries included) ≤ 10s.
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(10)))
                // Checksums only when the operation requires them: some S3-compatible stores reject
                // the newer default trailing checksums.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }

    private static AwsCredentialsProvider credentials(MinioProperties properties) {
        if (!StringUtils.hasText(properties.accessKey()) || !StringUtils.hasText(properties.secretKey())) {
            // Unset keys: requests are refused by MinIO (logged as a 500), the app itself still starts.
            log.warn("MINIO_ACCESS_KEY / MINIO_SECRET_KEY not set: avatar storage calls will be refused");
            return AnonymousCredentialsProvider.create();
        }
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
    }
}

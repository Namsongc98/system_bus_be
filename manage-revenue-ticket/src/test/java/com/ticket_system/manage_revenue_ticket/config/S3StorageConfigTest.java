package com.ticket_system.manage_revenue_ticket.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.net.ServerSocket;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * S3 client settings that only matter when MinIO misbehaves: a hung MinIO must not hold a request
 * thread (SDK defaults would wait 3 attempts x 30s), and missing keys must be visible in the log.
 */
@ExtendWith(OutputCaptureExtension.class)
class S3StorageConfigTest {

    private static final String WARNING = "MINIO_ACCESS_KEY / MINIO_SECRET_KEY not set";

    private static MinioProperties properties(String url, String accessKey, String secretKey) {
        return new MinioProperties(url, accessKey, secretKey, "user-avatars");
    }

    @Test
    void wholeCallIsCappedAtTenSeconds() {
        try (S3Client client = S3StorageConfig.buildClient(properties("http://127.0.0.1:9", "app", "app-key-123"))) {
            assertThat(client.serviceClientConfiguration().overrideConfiguration().apiCallTimeout())
                    .contains(Duration.ofSeconds(10));
        }
    }

    @Test
    void hungMinioFailsFastInsteadOfHoldingTheThread() throws Exception {
        // Accepts the TCP connection (backlog) but never answers, like a hung MinIO.
        try (ServerSocket silent = new ServerSocket(0);
             S3Client client = S3StorageConfig.buildClient(
                     properties("http://127.0.0.1:" + silent.getLocalPort(), "app", "app-key-123"))) {

            assertTimeoutPreemptively(Duration.ofSeconds(15), () ->
                    assertThatThrownBy(() -> client.getObjectAsBytes(GetObjectRequest.builder()
                            .bucket("user-avatars").key("avatars/1").build()))
                            .isInstanceOf(SdkClientException.class));
        }
    }

    @Test
    void missingKeysFallBackToAnonymousAndWarn(CapturedOutput output) {
        try (S3Client client = S3StorageConfig.buildClient(properties("http://127.0.0.1:9", "", " "))) {
            assertThat(client.serviceClientConfiguration().credentialsProvider())
                    .isInstanceOf(AnonymousCredentialsProvider.class);
        }
        assertThat(output).contains(WARNING);
    }

    @Test
    void configuredKeysAreUsedWithoutWarning(CapturedOutput output) {
        try (S3Client client = S3StorageConfig.buildClient(properties("http://127.0.0.1:9", "app", "app-key-123"))) {
            assertThat(client.serviceClientConfiguration().credentialsProvider())
                    .isInstanceOf(StaticCredentialsProvider.class);
        }
        assertThat(output).doesNotContain(WARNING);
    }
}

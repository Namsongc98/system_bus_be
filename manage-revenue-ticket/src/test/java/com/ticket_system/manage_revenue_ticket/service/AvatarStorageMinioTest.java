package com.ticket_system.manage_revenue_ticket.service;

import com.ticket_system.common.exception.ResourceNotFoundException;
import com.ticket_system.manage_revenue_ticket.config.MinioProperties;
import com.ticket_system.manage_revenue_ticket.config.S3StorageConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AvatarService against a real MinIO (same image as Infrastructure/minio): proves the S3 client
 * settings (path style, checksums, credentials) work end to end, not just against a mock.
 */
@Testcontainers(disabledWithoutDocker = true)   // skipped, not failed, on a host without Docker
class AvatarStorageMinioTest {
    // Dummy credentials for the throwaway test container only.
    private static final String ROOT_USER = "test-root";
    private static final String ROOT_PASSWORD = "test-root-password";
    private static final String BUCKET = "user-avatars";

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(
            DockerImageName.parse("pgsty/minio:RELEASE.2026-08-04T00-00-00Z"))
            .withCommand("server", "/data")
            .withEnv("MINIO_ROOT_USER", ROOT_USER)
            .withEnv("MINIO_ROOT_PASSWORD", ROOT_PASSWORD)
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    private static S3Client s3Client;
    private static AvatarService service;

    @BeforeAll
    static void setUp() {
        MinioProperties properties = new MinioProperties(
                "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000), ROOT_USER, ROOT_PASSWORD, BUCKET);
        s3Client = S3StorageConfig.buildClient(properties);
        s3Client.createBucket(b -> b.bucket(BUCKET));
        service = new AvatarService(s3Client, properties);
    }

    @AfterAll
    static void tearDown() {
        s3Client.close();
    }

    @Test
    void uploadedAvatarIsReadBackWithItsType() {
        service.upload(1L, new MockMultipartFile("file", "a.png", "image/png", AvatarServiceTest.PNG));

        AvatarService.Avatar avatar = service.download(1L);

        assertThat(avatar.content()).isEqualTo(AvatarServiceTest.PNG);
        assertThat(avatar.contentType()).isEqualTo("image/png");
        HeadObjectResponse head = s3Client.headObject(h -> h.bucket(BUCKET).key("avatars/1"));
        assertThat(head.contentLength()).isEqualTo(AvatarServiceTest.PNG.length);
    }

    @Test
    void secondUploadReplacesTheFirst() {
        service.upload(2L, new MockMultipartFile("file", AvatarServiceTest.JPEG));
        service.upload(2L, new MockMultipartFile("file", AvatarServiceTest.WEBP));

        AvatarService.Avatar avatar = service.download(2L);

        assertThat(avatar.content()).isEqualTo(AvatarServiceTest.WEBP);
        assertThat(avatar.contentType()).isEqualTo("image/webp");
    }

    @Test
    void userWithoutAvatarIsNotFound() {
        assertThatThrownBy(() -> service.download(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

package com.ticket_system.manage_revenue_ticket.service;

import com.ticket_system.common.exception.ResourceNotFoundException;
import com.ticket_system.manage_revenue_ticket.Dto.response.AvatarResponse;
import com.ticket_system.manage_revenue_ticket.config.MinioProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * One avatar per user, stored in the MinIO bucket under {@code avatars/{userId}}. Uploading again
 * overwrites it, so create and update are the same call and no database column is needed.
 * The type is taken from the file's first bytes, not from the client's Content-Type header.
 */
@Service
public class AvatarService {

    static final long MAX_BYTES = 2L * 1024 * 1024;

    private static final Logger log = LoggerFactory.getLogger(AvatarService.class);

    private final S3Client s3Client;
    private final String bucket;

    public AvatarService(S3Client s3Client, MinioProperties properties) {
        this.s3Client = s3Client;
        this.bucket = properties.bucket();
    }

    public AvatarResponse upload(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Chưa chọn ảnh");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("Ảnh tối đa 2MB");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String contentType = detectImageType(content);
        if (contentType == null) {
            throw new IllegalArgumentException("Chỉ nhận ảnh JPEG, PNG hoặc WEBP");
        }

        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(keyOf(userId))
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(content));
        log.info("Avatar saved: userId={} size={} type={}", userId, content.length, contentType);
        return new AvatarResponse(contentType, content.length);
    }

    public Avatar download(Long userId) {
        try {
            ResponseBytes<GetObjectResponse> object = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(keyOf(userId))
                    .build());
            byte[] content = object.asByteArray();
            // The stored Content-Type is not trusted: an object written outside the app (console, mc)
            // could claim text/html. Serve only what the bytes prove to be one of the 3 image types.
            String contentType = detectImageType(content);
            if (contentType == null) {
                // storedType is metadata set by whoever wrote the object: strip CR/LF so it cannot forge log lines
                log.error("Avatar object is not an image: userId={} storedType={}",
                        userId, String.valueOf(object.response().contentType()).replaceAll("\\p{Cntrl}", ""));
                throw new IllegalStateException("Stored avatar is not an image");
            }
            return new Avatar(content, contentType);
        } catch (NoSuchKeyException e) {
            throw new ResourceNotFoundException("Chưa có ảnh đại diện");
        }
    }

    static String keyOf(Long userId) {
        return "avatars/" + userId;
    }

    /** JPEG, PNG or WEBP by magic bytes; null for anything else. */
    static String detectImageType(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "image/png";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    public record Avatar(byte[] content, String contentType) {
    }
}

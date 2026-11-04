package com.ticket_system.manage_revenue_ticket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Object storage settings ({@code minio.*} in application.properties, values from MINIO_* env vars).
 * accessKey/secretKey belong to the app user created by Infrastructure/minio/init.sh, never root.
 */
@ConfigurationProperties(prefix = "minio")
public record MinioProperties(String url, String accessKey, String secretKey, String bucket) {
}

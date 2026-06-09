package com.ticket_system.manage_revenue_ticket.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/test-redis")
@RequiredArgsConstructor
public class RedisTestController {
  private final RedisTemplate<String, Object> redisTemplate;

  @GetMapping("/ping")
  public Map<String, Object> testConnection() {
    Map<String, Object> response = new HashMap<>();
    try {
      // 1. Kiểm tra PING
      String pong = Objects.requireNonNull(redisTemplate.getConnectionFactory())
        .getConnection()
        .ping();

      // 2. Thử Ghi và Đọc một Key tạm thời
      redisTemplate.opsForValue().set("test_key", "Connected Successfully!");
      String value = (String) redisTemplate.opsForValue().get("test_key");

      response.put("status", "SUCCESS");
      response.put("ping", pong);
      response.put("test_value", value);
      response.put("message", "Kết nối Redis Cluster hoàn hảo!");

    } catch (Exception e) {
      response.put("status", "ERROR");
      response.put("error_type", e.getClass().getName());
      response.put("error_message", e.getMessage());

      // Nếu lỗi do Cluster Redirection (Lỗi NAT Docker)
      if (e.getMessage() != null && e.getMessage().contains("172.19")) {
        response.put("suggestion", "Lỗi IP nội bộ Docker! Hãy kiểm tra bean LettuceClientConfigurationBuilderCustomizer trong RedisConfig.");
      }

      e.printStackTrace(); // In ra console để xem chi tiết
    }
    return response;
  }
}

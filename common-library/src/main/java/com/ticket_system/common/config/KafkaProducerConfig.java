package com.ticket_system.common.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;
@Configuration
public class KafkaProducerConfig {

  @Value("${spring.kafka.bootstrap-servers}")
  private String bootstrapServers;
  @Bean
  public ProducerFactory<String, Object> producerFactory() {
    Map<String, Object> configProps = new HashMap<>();
    configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

    // --- CẤU HÌNH TRÁNH MẤT DATA TẠI PRODUCER ---
    // 1. ACKS = ALL: Producer đợi tất cả bản sao xác nhận đã nhận hàng
    configProps.put(ProducerConfig.ACKS_CONFIG, "all");

    // 2. RETRIES: Nếu mạng lỗi, tự động gửi lại nhiều lần (max)
    configProps.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);

    // 3. ENABLE_IDEMPOTENCE: Đảm bảo dù gửi lại nhiều lần cũng không bị trùng dữ liệu (Duplicate)
    configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");

    // 4. MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION: Giữ đúng thứ tự tin nhắn khi retry
    configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5");

    return new DefaultKafkaProducerFactory<>(configProps);
  }

  @Bean
  public KafkaTemplate<String, Object> kafkaTemplate() {
    return new KafkaTemplate<>(producerFactory());
  }
}

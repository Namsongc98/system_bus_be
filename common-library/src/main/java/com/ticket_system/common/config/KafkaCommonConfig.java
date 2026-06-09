//package com.ticket_system.common.config;
//
//
//import org.apache.kafka.clients.producer.ProducerConfig;
//import org.apache.kafka.common.serialization.StringSerializer;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.kafka.core.DefaultKafkaProducerFactory;
//import org.springframework.kafka.core.KafkaTemplate;
//import org.springframework.kafka.core.ProducerFactory;
//import org.springframework.kafka.support.serializer.JsonSerializer;
//
//import java.util.HashMap;
//import java.util.Map;
//
//@Configuration
//public class KafkaCommonConfig {
//  @Value("${spring.kafka.bootstrap-servers}")
//  private String bootstrapServers;
//
//  @Bean
//  public ProducerFactory<String, Object> producerFactory() {
//    Map<String, Object> configProps = new HashMap<>();
//    configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
//    configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
//    configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
//
//    // --- CẤU HÌNH ACK VÀ AN TOÀN DỮ LIỆU ---
//
//    // "all" nghĩa là Leader + toàn bộ Follower (trong ISR) phải xác nhận
//    configProps.put(ProducerConfig.ACKS_CONFIG, "all");
//
//    // Số lần thử lại tối đa nếu Broker chưa gửi ACK kịp (do lag mạng, leader election)
//    configProps.put(ProducerConfig.RETRIES_CONFIG, 10);
//
//    // Thời gian chờ giữa các lần retry
//    configProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000);
//
//    // Quan trọng: Đảm bảo không ghi trùng dữ liệu khi retry
//    configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
//
//    return new DefaultKafkaProducerFactory<>(configProps);
//  }
//
//  @Bean
//  public KafkaTemplate<String, Object> kafkaTemplate() {
//    return new KafkaTemplate<>(producerFactory());
//  }
//}

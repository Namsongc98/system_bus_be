package com.ticket_system.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.ClusterTopologyRefreshOptions;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DnsResolver;
import io.lettuce.core.resource.MappingSocketAddressResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
public class RedisConfig {

  @Value("${spring.redis.cluster.nodes}")
  private List<String> clusterNodes;

  @Bean
  public RedisConnectionFactory redisConnectionFactory() {
    // 1. Map toàn bộ IP dải 172.x.x.x về 127.0.0.1
    ClientResources clientResources = ClientResources.builder()
      .socketAddressResolver(MappingSocketAddressResolver.create(DnsResolver.jvmDefault(),
        hostAndPort -> {
          // Nếu IP bắt đầu bằng 172 (IP nội bộ Docker), đổi thành 127.0.0.1
          if (hostAndPort.getHostText().startsWith("172.")) {
            return io.lettuce.core.internal.HostAndPort.of("127.0.0.1", hostAndPort.getPort());
          }
          return hostAndPort;
        }))
      .build();

    // 2. Cấu hình Cluster Nodes từ application.properties
    RedisClusterConfiguration clusterConfig = new RedisClusterConfiguration(clusterNodes);

    // 3. Cấu hình Topology Refresh
    ClusterTopologyRefreshOptions refreshOptions = ClusterTopologyRefreshOptions.builder()
      .enableAllAdaptiveRefreshTriggers()
      .enablePeriodicRefresh(Duration.ofMinutes(1))
      .build();

    ClusterClientOptions clientOptions = ClusterClientOptions.builder()
      .topologyRefreshOptions(refreshOptions)
      .build();

    // 4. Kết hợp ClientResources vào LettuceClientConfiguration
    LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
      .clientOptions(clientOptions)
      .clientResources(clientResources) // <--- Đưa tài nguyên vào đây
      .commandTimeout(Duration.ofSeconds(5))
      .build();

    return new LettuceConnectionFactory(clusterConfig, clientConfig);
  }
  // ==============================
  // Cache Manager
  // ==============================
  @Bean
  public RedisCacheManager cacheManager(RedisConnectionFactory factory) {

    RedisCacheConfiguration config =
      RedisCacheConfiguration.defaultCacheConfig()
        .serializeKeysWith(
          RedisSerializationContext.SerializationPair
            .fromSerializer(new StringRedisSerializer())
        )
        .serializeValuesWith(
          RedisSerializationContext.SerializationPair
            .fromSerializer(new GenericJackson2JsonRedisSerializer())
        );

    return RedisCacheManager.builder(factory)
      .cacheDefaults(config)
      .build();
  }

  // ==============================
  // 🔥 ADD THIS: RedisTemplate
  // ==============================
  @Bean
  public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {

    // 🔥 Tạo ObjectMapper custom
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    GenericJackson2JsonRedisSerializer serializer =
      new GenericJackson2JsonRedisSerializer(objectMapper);

    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(factory);

    template.setKeySerializer(new StringRedisSerializer());
    template.setHashKeySerializer(new StringRedisSerializer());

    template.setValueSerializer(serializer);
    template.setHashValueSerializer(serializer);

    template.afterPropertiesSet();
    return template;
  }

  @Bean
  public LettuceClientConfigurationBuilderCustomizer lettuceCustomizer() {
    return clientConfigurationBuilder -> {
      ClusterTopologyRefreshOptions refreshOptions = ClusterTopologyRefreshOptions.builder()
        .enableAllAdaptiveRefreshTriggers() // Tự động cập nhật khi có biến động
        .enablePeriodicRefresh() // Cập nhật định kỳ
        .build();

      clientConfigurationBuilder.clientOptions(ClusterClientOptions.builder()
        .topologyRefreshOptions(refreshOptions)
        .build());
    };
  }
}

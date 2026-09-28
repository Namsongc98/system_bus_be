package com.ticket_system.common.config;

import io.lettuce.core.internal.HostAndPort;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedisConfigTest {

    @Test
    void onHostRewritesDockerIpToLocalhostKeepingPort() {
        HostAndPort mapped = RedisConfig.remapDockerAddress(HostAndPort.of("172.30.0.12", 7002), true);

        assertThat(mapped.getHostText()).isEqualTo("127.0.0.1");
        assertThat(mapped.getPort()).isEqualTo(7002);
    }

    @Test
    void onHostLeavesNonDockerHostUnchanged() {
        HostAndPort original = HostAndPort.of("redis-1", 7001);

        assertThat(RedisConfig.remapDockerAddress(original, true)).isSameAs(original);
    }

    @Test
    void inContainerKeepsAnnouncedDockerIp() {
        HostAndPort original = HostAndPort.of("172.30.0.12", 7002);

        assertThat(RedisConfig.remapDockerAddress(original, false)).isSameAs(original);
    }
}

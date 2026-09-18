package io.lumina.agent.service;

import io.lumina.framework.cache.RedisCacheManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AgentInstanceRegistry} 实例心跳与判活单测
 *
 * @author Lumina Team
 * @since 3.12.1
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentInstanceRegistryTest {

    @Mock
    private RedisCacheManager redisCacheManager;

    private AgentInstanceRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AgentInstanceRegistry(redisCacheManager);
        registry.start();
    }

    @AfterEach
    void tearDown() {
        registry.stop();
    }

    @Test
    void startWritesHeartbeatWithTtl() {
        verify(redisCacheManager).set(eq("agent:instance:heartbeat:" + registry.selfId()),
                anyString(), any(Duration.class));
    }

    @Test
    void selfIdStableAndShort() {
        assertThat(registry.selfId()).hasSize(16).isEqualTo(registry.selfId());
    }

    @Test
    void isAliveTrueWhenHeartbeatExists() {
        when(redisCacheManager.exists("agent:instance:heartbeat:other01"))
                .thenReturn(true);
        assertThat(registry.isAlive("other01")).isTrue();
    }

    @Test
    void isAliveFalseWhenHeartbeatExpired() {
        when(redisCacheManager.exists(anyString())).thenReturn(false);
        assertThat(registry.isAlive("dead-instance-01")).isFalse();
    }

    @Test
    void isAliveFailSafeOnRedisError() {
        // fail-safe：Redis 异常按存活处理——判活失败宁可不回收，不可误杀在跑任务
        when(redisCacheManager.exists(anyString())).thenThrow(new RuntimeException("redis down"));
        assertThat(registry.isAlive("other02")).isTrue();
    }

    @Test
    void isAliveFalseForBlankId() {
        assertThat(registry.isAlive(null)).isFalse();
        assertThat(registry.isAlive(" ")).isFalse();
    }

    @Test
    void stopDeletesHeartbeatKey() {
        registry.stop();
        verify(redisCacheManager).delete("agent:instance:heartbeat:" + registry.selfId());
    }
}

package com.nexo.app.shared.infrastructure.lookup;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LookupRateLimiterTest {

    @Test
    void permiteAteOTetoERejeitaQuandoScriptRetornaContagemAcimaDoLimite() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        LookupRateLimitProperties properties = new LookupRateLimitProperties(true, 2, Duration.ofSeconds(30));
        LookupRateLimiter limiter = new LookupRateLimiter(redis, properties);
        when(redis.execute(any(), eq(List.of("lookup:rate-limit:user-1")), eq("30")))
                .thenReturn(2L, 3L);

        assertThat(limiter.tryAcquire("user-1")).isEqualTo(LookupRateLimitDecision.allow());
        LookupRateLimitDecision rejected = limiter.tryAcquire("user-1");
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isEqualTo(30);
    }

    @Test
    void falhaAbertaQuandoRedisIndisponivelOuRespostaNula() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        LookupRateLimitProperties properties = new LookupRateLimitProperties(true, 1, Duration.ofMinutes(1));
        LookupRateLimiter limiter = new LookupRateLimiter(redis, properties);
        when(redis.execute(any(), any(), any(String.class))).thenThrow(new RuntimeException("redis fora"));

        assertThat(limiter.tryAcquire("user-1")).isEqualTo(LookupRateLimitDecision.allow());
    }

    @Test
    void desabilitadoOuSemUsuarioNaoTocaRedis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        LookupRateLimiter limiter = new LookupRateLimiter(redis,
                new LookupRateLimitProperties(false, 1, Duration.ofMinutes(1)));

        assertThat(limiter.tryAcquire(null)).isEqualTo(LookupRateLimitDecision.allow());
        assertThat(limiter.tryAcquire("user-1")).isEqualTo(LookupRateLimitDecision.allow());
        org.mockito.Mockito.verifyNoInteractions(redis);
    }
}

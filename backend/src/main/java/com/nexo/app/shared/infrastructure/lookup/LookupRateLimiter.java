package com.nexo.app.shared.infrastructure.lookup;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Rate limiter fixed-window distribuído. O incremento e a definição do TTL
 * acontecem no mesmo script Lua para que requisições concorrentes não abram uma
 * janela maior por uma condição de corrida.
 */
@Component
@Slf4j
public class LookupRateLimiter {

    private static final RedisScript<Long> FIXED_WINDOW_SCRIPT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redis;
    private final LookupRateLimitProperties properties;

    public LookupRateLimiter(StringRedisTemplate redis, LookupRateLimitProperties properties) {
        this.redis = Objects.requireNonNull(redis, "redis é obrigatório");
        this.properties = Objects.requireNonNull(properties, "properties é obrigatório");
    }

    public LookupRateLimitDecision tryAcquire(String userKey) {
        if (!properties.enabled() || userKey == null || userKey.isBlank()) {
            return LookupRateLimitDecision.allow();
        }

        String key = "lookup:rate-limit:" + userKey.trim();
        try {
            Long count = redis.execute(FIXED_WINDOW_SCRIPT, List.of(key),
                    Long.toString(properties.windowSeconds()));
            // Fail-open também cobre respostas inesperadamente nulas do driver.
            if (count == null) {
                return LookupRateLimitDecision.allow();
            }
            return count <= properties.requestsPerMinute()
                    ? LookupRateLimitDecision.allow()
                    : LookupRateLimitDecision.reject(properties.windowSeconds());
        } catch (RuntimeException exception) {
            // Redis é uma proteção de tráfego, não deve derrubar autenticação ou os
            // endpoints de negócio quando estiver indisponível. Não registrar a chave
            // evita vazar o identificador do usuário nos logs.
            log.warn("Falha no rate limit de lookup; seguindo em fail-open (tipo={})",
                    exception.getClass().getSimpleName());
            return LookupRateLimitDecision.allow();
        }
    }
}

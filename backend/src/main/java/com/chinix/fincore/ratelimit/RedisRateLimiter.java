package com.chinix.fincore.ratelimit;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Ventana fija en Redis (app.redis.enabled=true): el contador es compartido entre todas las
 * instancias del backend. INCR es atómico; el primer incremento fija la expiración.
 * Solo se usa para limitar peticiones: nunca guarda saldos ni datos financieros.
 */
@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisRateLimiter implements RateLimiter {

  private final StringRedisTemplate redis;

  public RedisRateLimiter(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public boolean allow(String key, int limit, Duration window) {
    String redisKey = "ratelimit:" + key;
    Long count = redis.opsForValue().increment(redisKey);
    if (count != null && count == 1) redis.expire(redisKey, window);
    return count != null && count <= limit;
  }
}

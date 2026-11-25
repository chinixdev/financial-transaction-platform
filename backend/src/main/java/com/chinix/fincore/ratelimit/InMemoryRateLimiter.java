package com.chinix.fincore.ratelimit;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Ventana fija en memoria: suficiente para una sola instancia (modo local). */
@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryRateLimiter implements RateLimiter {

  private record Window(long start, AtomicInteger count) {}

  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  @Override
  public boolean allow(String key, int limit, Duration window) {
    long now = System.currentTimeMillis();
    Window w = windows.compute(key, (k, old) ->
      old == null || now - old.start() >= window.toMillis()
        ? new Window(now, new AtomicInteger())
        : old
    );
    return w.count().incrementAndGet() <= limit;
  }
}

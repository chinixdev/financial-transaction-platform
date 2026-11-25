package com.chinix.fincore.ratelimit;

import java.time.Duration;

/** Limita cuántas veces se puede hacer algo en una ventana de tiempo. */
public interface RateLimiter {
  /** true si la acción está permitida (y la cuenta); false si se superó el límite. */
  boolean allow(String key, int limit, Duration window);
}

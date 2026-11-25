package com.chinix.fincore.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Frena abusos por IP: login/registro (fuerza bruta) más estricto, y las operaciones de dinero
 * más holgado. Responde 429 al superar el límite.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

  private final RateLimiter limiter;
  private final int authLimit;
  private final int moneyLimit;

  public RateLimitFilter(
    RateLimiter limiter,
    @Value("${app.rate-limit.auth-per-minute:10}") int authLimit,
    @Value("${app.rate-limit.money-per-minute:60}") int moneyLimit
  ) {
    this.limiter = limiter;
    this.authLimit = authLimit;
    this.moneyLimit = moneyLimit;
  }

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain chain
  ) throws ServletException, IOException {
    String path = request.getRequestURI();
    boolean post = request.getMethod().equals("POST");
    String group = null;
    int limit = 0;
    if (post && path.startsWith("/api/auth/")) {
      group = "auth";
      limit = authLimit;
    } else if (post && (path.startsWith("/api/transfers") || path.startsWith("/api/payments"))) {
      group = "money";
      limit = moneyLimit;
    }
    if (group != null && !limiter.allow(group + ":" + request.getRemoteAddr(), limit, Duration.ofMinutes(1))) {
      response.setStatus(429);
      response.setHeader("Retry-After", "60");
      response.setContentType("application/json");
      response.getWriter().write("{\"error\":\"Too many requests\"}");
      return;
    }
    chain.doFilter(request, response);
  }
}

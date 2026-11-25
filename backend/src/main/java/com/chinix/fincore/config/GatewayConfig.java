package com.chinix.fincore.config;

import com.chinix.fincore.payment.*;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Configuration
public class GatewayConfig {

  /** Errores 4xx del proveedor (tarjeta rechazada, datos inválidos): reintentar no los arregla. */
  @Bean
  Retry paymentRetry(
    @Value("${app.resilience.retry-attempts:3}") int attempts,
    @Value("${app.resilience.retry-wait-ms:200}") long waitMs
  ) {
    return Retry.of(
      "payment-gateway",
      RetryConfig.custom()
        .maxAttempts(attempts)
        .waitDuration(Duration.ofMillis(waitMs))
        .ignoreExceptions(CallNotPermittedException.class, HttpClientErrorException.class)
        .build()
    );
  }

  /** Se abre si falla la mitad de las últimas 5 llamadas; se prueba de nuevo tras el enfriamiento. */
  @Bean
  CircuitBreaker paymentCircuitBreaker(
    @Value("${app.resilience.circuit-wait-seconds:30}") long waitSeconds
  ) {
    return CircuitBreaker.of(
      "payment-gateway",
      CircuitBreakerConfig.custom()
        .slidingWindowSize(5)
        .minimumNumberOfCalls(5)
        .failureRateThreshold(50)
        .waitDurationInOpenState(Duration.ofSeconds(waitSeconds))
        .ignoreExceptions(HttpClientErrorException.class)
        .build()
    );
  }

  /** Con STRIPE_SECRET_KEY (clave de prueba) usa Stripe; sin ella, el proveedor simulado. */
  @Bean
  PaymentGateway paymentGateway(
    @Value("${app.stripe.secret-key:}") String key,
    @Value("${app.stripe.currency:usd}") String currency,
    RestClient.Builder http,
    Retry retry,
    CircuitBreaker breaker
  ) {
    PaymentGateway provider = key.isBlank()
      ? new MockGateway()
      : new StripeGateway(key, currency, http);
    return new ResilientGateway(provider, retry, breaker);
  }
}

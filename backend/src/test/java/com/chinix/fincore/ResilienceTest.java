package com.chinix.fincore;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.chinix.fincore.payment.*;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

/** Prueba unitaria (Mockito) de Retry + Circuit Breaker sobre la pasarela. Sin Spring ni red. */
class ResilienceTest {

  static final BigDecimal TEN = new BigDecimal("10.00");

  PaymentGateway provider = mock(PaymentGateway.class);

  ResilientGateway gateway(int attempts, int window) {
    Retry retry = Retry.of(
      "t",
      RetryConfig.custom()
        .maxAttempts(attempts)
        .waitDuration(Duration.ofMillis(1))
        .ignoreExceptions(CallNotPermittedException.class, HttpClientErrorException.class)
        .build()
    );
    CircuitBreaker breaker = CircuitBreaker.of(
      "t",
      CircuitBreakerConfig.custom()
        .slidingWindowSize(window)
        .minimumNumberOfCalls(window)
        .failureRateThreshold(50)
        .waitDurationInOpenState(Duration.ofMinutes(1))
        .ignoreExceptions(HttpClientErrorException.class)
        .build()
    );
    return new ResilientGateway(provider, retry, breaker);
  }

  @Test
  void transientFailuresAreRetriedUntilTheProviderRecovers() {
    var ok = new PaymentGateway.Charge("pi_1", "pending");
    when(provider.createCharge(any(), any(), any()))
      .thenThrow(new RuntimeException("timeout"))
      .thenThrow(new RuntimeException("timeout"))
      .thenReturn(ok);

    var result = gateway(3, 100).createCharge(TEN, "PEN", "key");

    assertEquals(ok, result);
    verify(provider, times(3)).createCharge(any(), any(), any()); // misma Idempotency-Key en los 3
  }

  @Test
  void retriesStopAtTheConfiguredLimit() {
    when(provider.createCharge(any(), any(), any())).thenThrow(new RuntimeException("caído"));

    assertThrows(RuntimeException.class, () -> gateway(3, 100).createCharge(TEN, "PEN", "k"));

    verify(provider, times(3)).createCharge(any(), any(), any());
  }

  @Test
  void clientErrorsFromTheProviderAreNotRetried() {
    when(provider.createCharge(any(), any(), any()))
      .thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "tarjeta rechazada", HttpHeaders.EMPTY, new byte[0], null));

    assertThrows(HttpClientErrorException.class, () -> gateway(3, 100).createCharge(TEN, "PEN", "k"));

    verify(provider, times(1)).createCharge(any(), any(), any()); // reintentar no arregla un 4xx
  }

  @Test
  void circuitOpensAfterRepeatedFailuresAndStopsCallingTheProvider() {
    when(provider.createCharge(any(), any(), any())).thenThrow(new RuntimeException("caído"));
    ResilientGateway gw = gateway(1, 4); // sin reintentos; ventana de 4 llamadas

    for (int i = 0; i < 4; i++) {
      assertThrows(RuntimeException.class, () -> gw.createCharge(TEN, "PEN", "k"));
    }
    assertEquals(CircuitBreaker.State.OPEN, gw.circuitState());

    // Con el circuito abierto se rechaza al instante, SIN llamar al proveedor.
    Exception rejected = assertThrows(RuntimeException.class, () -> gw.createCharge(TEN, "PEN", "k"));
    assertTrue(ResilientGateway.isCircuitOpen(rejected));
    verify(provider, times(4)).createCharge(any(), any(), any());
  }
}

package com.chinix.fincore.payment;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.math.BigDecimal;
import java.util.List;
import java.util.function.Supplier;

/**
 * Protege cualquier pasarela con Retry y Circuit Breaker (Resilience4j).
 *  - Retry: reintenta fallos transitorios. Es seguro porque el cobro lleva Idempotency-Key.
 *  - Circuit Breaker: si el proveedor falla de forma repetida, "abre el circuito" y rechaza las
 *    llamadas al instante (sin esperar timeouts) hasta que pase el tiempo de enfriamiento.
 * Cada intento del Retry cuenta para el Circuit Breaker (el breaker queda por dentro).
 */
public class ResilientGateway implements PaymentGateway {

  private final PaymentGateway delegate;
  private final Retry retry;
  private final CircuitBreaker breaker;

  public ResilientGateway(PaymentGateway delegate, Retry retry, CircuitBreaker breaker) {
    this.delegate = delegate;
    this.retry = retry;
    this.breaker = breaker;
  }

  @Override
  public String name() {
    return delegate.name();
  }

  @Override
  public Charge createCharge(BigDecimal amount, String currency, String idempotencyKey) {
    return protect(() -> delegate.createCharge(amount, currency, idempotencyKey));
  }

  @Override
  public List<ProviderCharge> listCharges() {
    return protect(delegate::listCharges);
  }

  public CircuitBreaker.State circuitState() {
    return breaker.getState();
  }

  private <T> T protect(Supplier<T> call) {
    return Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(breaker, call)).get();
  }

  /** Para diagnóstico: ¿se rechazó la llamada porque el circuito está abierto? */
  public static boolean isCircuitOpen(Throwable t) {
    return t instanceof CallNotPermittedException;
  }
}

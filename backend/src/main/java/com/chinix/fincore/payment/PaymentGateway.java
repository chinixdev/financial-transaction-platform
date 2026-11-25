package com.chinix.fincore.payment;

import java.math.BigDecimal;
import java.util.List;

/**
 * Abstracción del proveedor de pagos. Para añadir otro (PayPal, Culqi, ...) basta con
 * implementar esta interfaz; el resto del sistema no cambia.
 */
public interface PaymentGateway {
  /** Resultado de crear el cobro. status: "succeeded", "pending" o "failed". */
  record Charge(String providerRef, String status) {}

  /** Cobro tal como lo registra el proveedor (para la conciliación). */
  record ProviderCharge(String providerRef, BigDecimal amount, String status) {}

  String name();

  /** idempotencyKey evita que un reintento cree dos cobros en el proveedor. */
  Charge createCharge(BigDecimal amount, String currency, String idempotencyKey);

  /** Cobros que el proveedor tiene registrados (los más recientes). */
  List<ProviderCharge> listCharges();
}

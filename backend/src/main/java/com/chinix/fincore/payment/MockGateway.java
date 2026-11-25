package com.chinix.fincore.payment;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Proveedor simulado (se usa si no hay clave de Stripe). Responde "pending" a quien crea el
 * cobro, pero en SU registro el cliente ya pagó ("succeeded"): el webhook es solo el aviso.
 * Así, si el webhook se pierde, la conciliación detecta y recupera el pago.
 */
public class MockGateway implements PaymentGateway {

  private final Map<String, ProviderCharge> charges = new ConcurrentHashMap<>();

  @Override
  public String name() {
    return "MOCK";
  }

  @Override
  public Charge createCharge(BigDecimal amount, String currency, String key) {
    String ref = "mock_pi_" + UUID.randomUUID();
    charges.put(ref, new ProviderCharge(ref, amount, "succeeded"));
    return new Charge(ref, "pending");
  }

  @Override
  public List<ProviderCharge> listCharges() {
    return List.copyOf(charges.values());
  }
}

package com.chinix.fincore;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.chinix.fincore.payment.*;
import com.chinix.fincore.payment.PaymentGateway.ProviderCharge;
import com.chinix.fincore.reconciliation.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Conciliación: detecta diferencias con el proveedor y recupera webhooks perdidos. */
@SpringBootTest(properties = "app.outbox.delay-ms=3600000")
class ReconciliationTest extends TestBase {

  @Autowired PaymentService payments;
  @Autowired ReconciliationService reconciliation;
  @MockitoBean PaymentGateway gateway;

  List<ProviderCharge> providerRecords = new ArrayList<>();

  @BeforeEach
  void stubProvider() {
    when(gateway.name()).thenReturn("MOCK");
    when(gateway.createCharge(any(), any(), any()))
      .thenAnswer(i -> new PaymentGateway.Charge("pi_" + UUID.randomUUID(), "pending"));
    when(gateway.listCharges()).thenAnswer(i -> List.copyOf(providerRecords));
  }

  Payment pay(Wallet w, String amount) {
    return payments.create(w.user(), w.accountId(), new BigDecimal(amount), key());
  }

  /** Diferencias abiertas de una referencia concreta (la base es compartida con otras pruebas). */
  List<Finding> findingsFor(String ref) {
    return reconciliation.run().stream().filter(f -> f.providerRef.equals(ref)).toList();
  }

  @Test
  void matchingRecordsProduceNoFindings() {
    Payment p = pay(wallet("0.00"), "20.00");
    providerRecords.add(new ProviderCharge(p.providerRef, new BigDecimal("20.00"), "pending"));

    assertTrue(findingsFor(p.providerRef).isEmpty());
  }

  @Test
  void lostWebhookIsDetectedAndRecoveredExactlyOnce() {
    Wallet w = wallet("0.00");
    Payment p = pay(w, "40.00"); // PENDING: el webhook "se perdió"
    providerRecords.add(new ProviderCharge(p.providerRef, new BigDecimal("40.00"), "succeeded"));

    List<Finding> found = findingsFor(p.providerRef);
    assertEquals(1, found.size());
    assertEquals(Finding.STATUS_MISMATCH, found.get(0).type);
    assertEquals(new BigDecimal("0.00"), balance(w));

    reconciliation.resolve(found.get(0).id);
    reconciliation.resolve(found.get(0).id); // pulsar dos veces no acredita dos veces

    assertEquals(new BigDecimal("40.00"), balance(w));
    assertTrue(findingsFor(p.providerRef).isEmpty()); // ya no hay diferencia abierta
    assertTrue(ledgerIsBalanced());
  }

  @Test
  void runningTwiceDoesNotDuplicateOpenFindings() {
    Payment p = pay(wallet("0.00"), "15.00");
    providerRecords.add(new ProviderCharge(p.providerRef, new BigDecimal("15.00"), "succeeded"));

    findingsFor(p.providerRef);

    assertEquals(1, findingsFor(p.providerRef).size());
  }

  @Test
  void providerChargeWithoutInternalPaymentNeedsManualReview() {
    providerRecords.add(new ProviderCharge("pi_ghost_" + UUID.randomUUID(), new BigDecimal("99.00"), "succeeded"));
    String ref = providerRecords.get(0).providerRef();

    List<Finding> found = findingsFor(ref);

    assertEquals(Finding.MISSING_INTERNALLY, found.get(0).type);
    assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> reconciliation.resolve(found.get(0).id)));
  }

  @Test
  void creditedPaymentMissingAtTheProviderIsFlagged() {
    Wallet w = wallet("0.00");
    Payment p = pay(w, "30.00");
    payments.complete(p.providerRef); // acreditado aquí, pero el proveedor no lo tiene

    List<Finding> found = findingsFor(p.providerRef);

    assertEquals(Finding.MISSING_AT_PROVIDER, found.get(0).type);
    assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> reconciliation.resolve(found.get(0).id)));
    assertEquals(new BigDecimal("30.00"), balance(w)); // nunca se corrige solo
  }

  @Test
  void amountDifferenceIsFlaggedAndNotAutoFixed() {
    Wallet w = wallet("0.00");
    Payment p = pay(w, "50.00");
    providerRecords.add(new ProviderCharge(p.providerRef, new BigDecimal("55.00"), "succeeded"));

    List<Finding> found = findingsFor(p.providerRef);

    assertEquals(Finding.AMOUNT_MISMATCH, found.get(0).type);
    assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> reconciliation.resolve(found.get(0).id)));
    assertEquals(new BigDecimal("0.00"), balance(w));
  }
}

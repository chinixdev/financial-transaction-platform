package com.chinix.fincore;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.chinix.fincore.payment.*;
import com.chinix.fincore.webhook.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Pagos con pasarela y webhooks: firma, duplicados, fallos del proveedor. */
@SpringBootTest(properties = "app.outbox.delay-ms=3600000")
class WebhookTest extends TestBase {

  @Autowired PaymentService payments;
  @Autowired WebhookService webhooks;
  @MockitoBean PaymentGateway gateway; // proveedor de pagos simulado con Mockito

  @BeforeEach
  void stubGateway() {
    when(gateway.name()).thenReturn("MOCK");
    when(gateway.createCharge(any(), any(), any()))
      .thenAnswer(i -> new PaymentGateway.Charge("pi_" + UUID.randomUUID(), "pending"));
  }

  String event(String eventId, String type, String providerRef) {
    return "{\"id\":\"" + eventId + "\",\"type\":\"" + type + "\"," +
      "\"data\":{\"object\":{\"id\":\"" + providerRef + "\"}}}";
  }

  WebhookService.Result deliver(String payload) {
    String sig = StripeSignature.header(payload, webhooks.secret(), Instant.now().getEpochSecond());
    return webhooks.handle(payload, sig);
  }

  Payment pay(Wallet w, String amount) {
    return payments.create(w.user(), w.accountId(), new BigDecimal(amount), key());
  }

  // ---------- firma ----------

  @Test
  void invalidOrMissingOrStaleSignatureIsRejected() {
    String payload = event("evt_x", "payment_intent.succeeded", "pi_x");
    long now = Instant.now().getEpochSecond();

    assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> webhooks.handle(payload, "t=" + now + ",v1=deadbeef")));
    assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> webhooks.handle(payload, null)));
    String old = StripeSignature.header(payload, webhooks.secret(), now - 3600); // firma válida pero vieja
    assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> webhooks.handle(payload, old)));
  }

  @Test
  void tamperedPayloadFailsSignature() {
    String payload = event("evt_y", "payment_intent.succeeded", "pi_y");
    String sig = StripeSignature.header(payload, webhooks.secret(), Instant.now().getEpochSecond());
    String tampered = payload.replace("pi_y", "pi_other");

    assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> webhooks.handle(tampered, sig)));
  }

  // ---------- pago + webhook ----------

  @Test
  void paymentStaysPendingUntilWebhookConfirmsThenCreditsWithBalancedLedger() {
    Wallet w = wallet("0.00");
    Payment p = pay(w, "80.00");

    assertEquals("PENDING", p.status);
    assertEquals(new BigDecimal("0.00"), balance(w)); // sin confirmación no hay saldo

    assertEquals(WebhookService.Result.PROCESSED, deliver(event("evt_1", "payment_intent.succeeded", p.providerRef)));

    assertEquals(new BigDecimal("80.00"), balance(w));
    assertTrue(ledgerIsBalanced());
  }

  @Test
  void duplicateWebhookDeliveryCreditsOnlyOnce() {
    Wallet w = wallet("0.00");
    Payment p = pay(w, "50.00");
    String payload = event("evt_dup_" + UUID.randomUUID(), "payment_intent.succeeded", p.providerRef);

    assertEquals(WebhookService.Result.PROCESSED, deliver(payload));
    assertEquals(WebhookService.Result.DUPLICATE, deliver(payload));

    assertEquals(new BigDecimal("50.00"), balance(w));
  }

  @Test
  void tenSimultaneousDeliveriesOfTheSameEventCreditOnlyOnce() throws Exception {
    Wallet w = wallet("0.00");
    Payment p = pay(w, "50.00");
    String payload = event("evt_race_" + UUID.randomUUID(), "payment_intent.succeeded", p.providerRef);

    runConcurrently(10, i -> deliver(payload));

    assertEquals(new BigDecimal("50.00"), balance(w));
  }

  @Test
  void differentEventsForTheSamePaymentCreditOnlyOnce() {
    Wallet w = wallet("0.00");
    Payment p = pay(w, "30.00");

    deliver(event("evt_a_" + UUID.randomUUID(), "payment_intent.succeeded", p.providerRef));
    deliver(event("evt_b_" + UUID.randomUUID(), "payment_intent.succeeded", p.providerRef));

    assertEquals(new BigDecimal("30.00"), balance(w));
  }

  @Test
  void failedPaymentEventNeverCredits() {
    Wallet w = wallet("0.00");
    Payment p = pay(w, "30.00");

    deliver(event("evt_f_" + UUID.randomUUID(), "payment_intent.payment_failed", p.providerRef));
    deliver(event("evt_s_" + UUID.randomUUID(), "payment_intent.succeeded", p.providerRef));

    assertEquals(new BigDecimal("0.00"), balance(w)); // ya estaba FAILED
  }

  @Test
  void unknownEventTypeIsRecordedAndIgnored() {
    assertEquals(
      WebhookService.Result.PROCESSED,
      deliver(event("evt_u_" + UUID.randomUUID(), "customer.created", "cus_1"))
    );
  }

  // ---------- idempotencia y fallos del proveedor ----------

  @Test
  void samePaymentKeyCallsTheProviderOnceAndReturnsTheSamePayment() {
    Wallet w = wallet("0.00");
    String k = key();

    Payment first = payments.create(w.user(), w.accountId(), new BigDecimal("10.00"), k);
    Payment second = payments.create(w.user(), w.accountId(), new BigDecimal("10.00"), k);

    assertEquals(first.id, second.id);
    verify(gateway, times(1)).createCharge(any(), any(), any());
  }

  @Test
  void samePaymentKeyWithDifferentAmountIsConflict() {
    Wallet w = wallet("0.00");
    String k = key();
    payments.create(w.user(), w.accountId(), new BigDecimal("10.00"), k);

    assertEquals(
      HttpStatus.CONFLICT,
      statusOf(() -> payments.create(w.user(), w.accountId(), new BigDecimal("11.00"), k))
    );
  }

  @Test
  void providerFailureMarksPaymentFailedAndReturnsBadGateway() {
    when(gateway.createCharge(any(), any(), any())).thenThrow(new RuntimeException("Stripe caído"));
    Wallet w = wallet("0.00");

    assertEquals(HttpStatus.BAD_GATEWAY, statusOf(() -> pay(w, "10.00")));

    assertEquals("FAILED", payments.mine(w.user()).get(0).status);
    assertEquals(new BigDecimal("0.00"), balance(w));
  }

  @Test
  void cannotRechargeSomeoneElsesAccount() {
    Wallet owner = wallet("0.00"), thief = wallet("0.00");

    assertEquals(
      HttpStatus.FORBIDDEN,
      statusOf(() -> payments.create(thief.user(), owner.accountId(), new BigDecimal("10.00"), key()))
    );
  }

  @Test
  void synchronousProviderConfirmationCreditsImmediately() {
    when(gateway.createCharge(any(), any(), any()))
      .thenAnswer(i -> new PaymentGateway.Charge("pi_" + UUID.randomUUID(), "succeeded"));
    Wallet w = wallet("0.00");

    Payment p = pay(w, "25.00");

    assertEquals("SUCCEEDED", p.status);
    assertEquals(new BigDecimal("25.00"), balance(w));
  }
}

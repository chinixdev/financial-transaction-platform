package com.chinix.fincore;

import static org.junit.jupiter.api.Assertions.*;

import com.chinix.fincore.fraud.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

/** Antifraude con los umbrales por defecto (application.yml). */
@SpringBootTest(properties = "app.outbox.delay-ms=3600000")
class FraudTest extends TestBase {

  @Autowired FraudAlertRepository alerts;

  // ---------- reglas puras (sin base de datos) ----------

  FraudEngine engine = new FraudEngine(
    new FraudProperties(new BigDecimal("5000"), 5, 10, new BigDecimal("0.90"), 70),
    null,
    null,
    null
  );

  @Test
  void normalOperationIsLowRiskAndAllowed() {
    var d = engine.evaluate(new BigDecimal("50"), new BigDecimal("1000"), 0);
    assertEquals("LOW", d.level());
    assertFalse(d.blocked());
  }

  @Test
  void highAmountAloneIsBlocked() {
    var d = engine.evaluate(new BigDecimal("5000"), new BigDecimal("100000"), 0);
    assertEquals(List.of("HIGH_AMOUNT"), d.reasons());
    assertTrue(d.blocked());
  }

  @Test
  void velocityAloneIsMediumButAllowed() {
    var d = engine.evaluate(new BigDecimal("10"), new BigDecimal("1000"), 5);
    assertEquals("MEDIUM", d.level());
    assertFalse(d.blocked());
  }

  @Test
  void velocityPlusDrainingTheBalanceIsBlocked() {
    var d = engine.evaluate(new BigDecimal("90"), new BigDecimal("95"), 5);
    assertEquals(List.of("VELOCITY", "DRAIN_BALANCE"), d.reasons());
    assertTrue(d.blocked());
  }

  // ---------- integración: bloqueo real y alerta persistida ----------

  @Test
  void suspiciousTransferIsBlockedNothingMovesAndAlertIsSaved() {
    Wallet a = wallet("10000.00"), b = wallet("0.00");

    assertEquals(
      HttpStatus.UNPROCESSABLE_ENTITY,
      statusOf(() -> send(a, b, "5000.00", key()))
    );

    assertEquals(new BigDecimal("10000.00"), balance(a)); // no se movió dinero
    var saved = alerts.findTop50ByUserIdOrderByCreatedAtDesc(a.user());
    assertEquals(1, saved.size()); // la alerta sobrevive al rechazo
    assertEquals("BLOCKED", saved.get(0).decision);
    assertEquals("HIGH", saved.get(0).level);
  }

  @Test
  void rapidTransfersTriggerVelocityAlertAndThenBlockWhenDrainingTheBalance() {
    Wallet a = wallet("100.00"), b = wallet("0.00");
    for (int i = 0; i < 5; i++) send(a, b, "1.00", key()); // saldo 95.00

    // 6.ª operación: velocidad (+40) y vaciado de saldo (+30) = 70 -> bloqueada
    assertEquals(
      HttpStatus.UNPROCESSABLE_ENTITY,
      statusOf(() -> send(a, b, "90.00", key()))
    );
    assertEquals(new BigDecimal("95.00"), balance(a));

    // Una operación pequeña solo genera alerta (riesgo medio) y se permite.
    send(a, b, "1.00", key());
    var saved = alerts.findTop50ByUserIdOrderByCreatedAtDesc(a.user());
    assertTrue(saved.stream().anyMatch(x -> x.decision.equals("ALLOWED") && x.level.equals("MEDIUM")));
  }

  @Test
  void retryOfAnAlreadyProcessedRequestIsNotBlockedByFraud() {
    Wallet a = wallet("100.00"), b = wallet("0.00");
    String k = key();
    for (int i = 0; i < 4; i++) send(a, b, "1.00", key());
    var first = send(a, b, "1.00", k); // quinta operación

    // Reintento con la misma clave: devuelve el resultado original sin reevaluar el riesgo.
    assertEquals(first.id(), send(a, b, "1.00", k).id());
  }
}

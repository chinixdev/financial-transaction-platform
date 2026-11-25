package com.chinix.fincore;

import static org.junit.jupiter.api.Assertions.*;

import com.chinix.fincore.account.*;
import com.chinix.fincore.auth.*;
import com.chinix.fincore.outbox.*;
import com.chinix.fincore.transfer.*;
import com.chinix.fincore.transfer.TransferService.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Pruebas de integración del núcleo financiero contra H2 (modo PostgreSQL).
 * Los umbrales de antifraude se suben para que estas pruebas midan concurrencia e idempotencia
 * sin que el antifraude interfiera (el antifraude tiene sus propias pruebas).
 */
@SpringBootTest(
  properties = {
    "app.outbox.delay-ms=3600000",
    "app.fraud.max-amount=1000000",
    "app.fraud.max-per-window=100000",
    "app.fraud.drain-ratio=2",
  }
)
class TransferServiceTest extends TestBase {

  @Autowired OutboxRepository outbox;
  @Autowired OutboxPublisher publisher;

  // ---------- transferencia básica y saldo ----------

  @Test
  void transferMovesMoneyAndWritesBalancedLedger() {
    Wallet a = wallet("100.00"), b = wallet("0.00");

    Receipt r = send(a, b, "30.50", "k-" + UUID.randomUUID());

    assertEquals(new BigDecimal("69.50"), balance(a));
    assertEquals(new BigDecimal("30.50"), balance(b));
    List<LedgerEntry> entries = ledger
      .findAll()
      .stream()
      .filter(e -> e.transferId.equals(r.id()))
      .toList();
    assertEquals(2, entries.size());
    assertEquals(
      entries.stream().filter(e -> e.direction.equals("DEBIT")).findFirst().get().amount,
      entries.stream().filter(e -> e.direction.equals("CREDIT")).findFirst().get().amount
    );
    assertTrue(
      ledger.total("DEBIT").compareTo(ledger.total("CREDIT")) == 0,
      "débitos y créditos globales deben coincidir"
    );
  }

  @Test
  void insufficientFundsIsRejectedAndNothingChanges() {
    Wallet a = wallet("10.00"), b = wallet("0.00");

    assertEquals(
      HttpStatus.BAD_REQUEST,
      statusOf(() -> send(a, b, "10.01", "k-" + UUID.randomUUID()))
    );
    assertEquals(new BigDecimal("10.00"), balance(a));
    assertEquals(new BigDecimal("0.00"), balance(b));
  }

  @Test
  void cannotSpendFromSomeoneElsesAccount() {
    Wallet a = wallet("50.00"), b = wallet("0.00");
    Request r = new Request(a.accountId(), b.number(), new BigDecimal("5"), null);

    assertEquals(
      HttpStatus.FORBIDDEN,
      statusOf(() -> service.send(b.user(), r, "k-" + UUID.randomUUID()))
    );
  }

  @Test
  void invalidAmountsAreRejected() {
    Wallet a = wallet("50.00"), b = wallet("0.00");
    for (String bad : List.of("0", "-5", "1.999")) {
      assertEquals(
        HttpStatus.BAD_REQUEST,
        statusOf(() -> send(a, b, bad, "k-" + UUID.randomUUID())),
        "importe " + bad
      );
    }
  }

  // ---------- idempotencia ----------

  @Test
  void sameKeyAndPayloadReturnsOriginalTransferWithoutChargingTwice() {
    Wallet a = wallet("100.00"), b = wallet("0.00");
    String key = "k-" + UUID.randomUUID();

    Receipt first = send(a, b, "40.00", key);
    Receipt second = send(a, b, "40.00", key);

    assertEquals(first.id(), second.id());
    assertEquals(new BigDecimal("60.00"), balance(a));
  }

  @Test
  void sameKeyWithDifferentPayloadReturnsConflict() {
    Wallet a = wallet("100.00"), b = wallet("0.00");
    String key = "k-" + UUID.randomUUID();
    send(a, b, "40.00", key);

    assertEquals(HttpStatus.CONFLICT, statusOf(() -> send(a, b, "41.00", key)));
  }

  @Test
  void tenSimultaneousRequestsWithSameKeyChargeOnlyOnce() throws Exception {
    Wallet a = wallet("100.00"), b = wallet("0.00");
    String key = "k-" + UUID.randomUUID();

    runConcurrently(10, i -> send(a, b, "10.00", key));

    assertEquals(new BigDecimal("90.00"), balance(a));
  }

  // ---------- concurrencia ----------

  @Test
  void concurrentTransfersNeverOverdrawTheAccount() throws Exception {
    Wallet a = wallet("100.00"), b = wallet("0.00");

    // 20 intentos de 20.00 con saldo 100.00: solo 5 pueden tener éxito.
    int ok = runConcurrently(20, i -> send(a, b, "20.00", "k-" + UUID.randomUUID()));

    assertEquals(5, ok);
    assertEquals(new BigDecimal("0.00"), balance(a));
    assertEquals(new BigDecimal("100.00"), balance(b));
  }

  @Test
  void oppositeTransfersAtTheSameTimeDoNotDeadlockAndConserveMoney() throws Exception {
    Wallet a = wallet("500.00"), b = wallet("500.00");

    runConcurrently(20, i ->
      i % 2 == 0
        ? send(a, b, "10.00", "k-" + UUID.randomUUID())
        : send(b, a, "10.00", "k-" + UUID.randomUUID())
    );

    assertEquals(new BigDecimal("1000.00"), balance(a).add(balance(b)));
  }

  // ---------- reembolsos ----------

  @Test
  void partialRefundsThenFullRestoreBalancesAndLedgerStaysBalanced() {
    Wallet a = wallet("100.00"), b = wallet("0.00");
    Receipt t = send(a, b, "60.00", "k-" + UUID.randomUUID());

    service.refund(b.user(), t.id(), new BigDecimal("20.00"), "r-" + UUID.randomUUID());
    assertEquals("PARTIALLY_REFUNDED", statusOfTransfer(t.id()));

    service.refund(b.user(), t.id(), null, "r-" + UUID.randomUUID()); // resto: 40.00
    assertEquals("REFUNDED", statusOfTransfer(t.id()));

    assertEquals(new BigDecimal("100.00"), balance(a));
    assertEquals(new BigDecimal("0.00"), balance(b));
    assertTrue(ledger.total("DEBIT").compareTo(ledger.total("CREDIT")) == 0);
  }

  @Test
  void refundCannotExceedTheOriginalAmount() {
    Wallet a = wallet("100.00"), b = wallet("0.00");
    Receipt t = send(a, b, "50.00", "k-" + UUID.randomUUID());
    service.refund(b.user(), t.id(), new BigDecimal("30.00"), "r-" + UUID.randomUUID());

    assertEquals(
      HttpStatus.BAD_REQUEST,
      statusOf(() ->
        service.refund(b.user(), t.id(), new BigDecimal("20.01"), "r-" + UUID.randomUUID())
      )
    );
  }

  @Test
  void onlyTheRecipientCanRefund() {
    Wallet a = wallet("100.00"), b = wallet("0.00");
    Receipt t = send(a, b, "50.00", "k-" + UUID.randomUUID());

    assertEquals(
      HttpStatus.FORBIDDEN,
      statusOf(() -> service.refund(a.user(), t.id(), null, "r-" + UUID.randomUUID()))
    );
  }

  @Test
  void duplicatedRefundRequestRefundsOnlyOnce() {
    Wallet a = wallet("100.00"), b = wallet("0.00");
    Receipt t = send(a, b, "50.00", "k-" + UUID.randomUUID());
    String key = "r-" + UUID.randomUUID();

    Receipt first = service.refund(b.user(), t.id(), new BigDecimal("10.00"), key);
    Receipt second = service.refund(b.user(), t.id(), new BigDecimal("10.00"), key);

    assertEquals(first.id(), second.id());
    assertEquals(new BigDecimal("60.00"), balance(a)); // 100 - 50 + 10
  }

  @Test
  void simultaneousRefundsCannotRefundMoreThanTheOriginal() throws Exception {
    Wallet a = wallet("100.00"), b = wallet("0.00");
    Receipt t = send(a, b, "50.00", "k-" + UUID.randomUUID());

    // 10 reembolsos de 20.00 sobre 50.00: solo 2 caben (40.00).
    int ok = runConcurrently(10, i ->
      service.refund(b.user(), t.id(), new BigDecimal("20.00"), "r-" + UUID.randomUUID())
    );

    assertEquals(2, ok);
    assertEquals(new BigDecimal("90.00"), balance(a));
  }

  // ---------- outbox ----------

  @Test
  void transferCreatesAnOutboxEventThatIsPublished() {
    Wallet a = wallet("100.00"), b = wallet("0.00");
    long pendingBefore = outbox.countByStatus("PENDING");
    send(a, b, "1.00", "k-" + UUID.randomUUID());
    assertEquals(pendingBefore + 1, outbox.countByStatus("PENDING"));

    publisher.publishPending();

    assertEquals(0, outbox.countByStatus("PENDING"));
  }

  // ---------- utilidades ----------

  String statusOfTransfer(UUID id) {
    return service
      .mine(
        accounts.findById(
          ledger.findAll().stream().filter(e -> e.transferId.equals(id)).findFirst().get().accountId
        ).get().ownerId
      )
      .stream()
      .filter(t -> t.id.equals(id))
      .findFirst()
      .get().status;
  }
}

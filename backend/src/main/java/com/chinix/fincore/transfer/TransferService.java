package com.chinix.fincore.transfer;

import com.chinix.fincore.account.*;
import com.chinix.fincore.common.Money;
import com.chinix.fincore.fraud.FraudEngine;
import com.chinix.fincore.outbox.Outbox;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Núcleo financiero. Cada operación es una única transacción ACID que:
 *  1. bloquea las dos cuentas (siempre en el mismo orden, para evitar deadlocks),
 *  2. valida reglas, 3. mueve saldos, 4. escribe el ledger (débito + crédito),
 *  5. guarda el evento en el outbox y 6. registra la clave de idempotencia.
 */
@Service
public class TransferService {

  private final AccountRepository accounts;
  private final TransferRepository transfers;
  private final LedgerRepository ledger;
  private final IdempotencyRepository keys;
  private final Outbox outbox;
  private final FraudEngine fraud;
  private final TransactionTemplate tx;

  /** Cuenta "mundo exterior" (V3): contrapartida contable de las recargas. */
  public static final UUID EXTERNAL_ACCOUNT = UUID.fromString(
    "00000000-0000-0000-0000-000000000002"
  );

  public TransferService(
    AccountRepository accounts,
    TransferRepository transfers,
    LedgerRepository ledger,
    IdempotencyRepository keys,
    Outbox outbox,
    FraudEngine fraud,
    TransactionTemplate tx
  ) {
    this.accounts = accounts;
    this.transfers = transfers;
    this.ledger = ledger;
    this.keys = keys;
    this.outbox = outbox;
    this.fraud = fraud;
    this.tx = tx;
  }

  public record Request(
    UUID sourceAccountId,
    String destinationAccountNumber,
    BigDecimal amount,
    String reference
  ) {}

  public record Receipt(
    UUID id,
    String status,
    BigDecimal amount,
    String reference
  ) {}

  /**
   * Orquesta: validar -> (si es reintento, devolver el resultado) -> antifraude -> transacción.
   * No es @Transactional a propósito: el antifraude guarda su alerta en una transacción
   * propia y terminada, antes de abrir la del dinero (así no se agotan conexiones del pool).
   */
  public Receipt send(UUID user, Request r, String key) {
    requireKey(key);
    BigDecimal amount = Money.valid(r.amount());
    if (r.sourceAccountId() == null || r.destinationAccountNumber() == null) {
      throw bad("Account required");
    }
    String hash = hash(
      "SEND|" +
        r.sourceAccountId() +
        "|" +
        r.destinationAccountNumber() +
        "|" +
        amount +
        "|" +
        Objects.toString(r.reference(), "")
    );
    Receipt previous = tx.execute(s -> replay(user, key, hash));
    if (previous != null) return previous;

    fraud.check(user, r.sourceAccountId(), amount);
    return tx.execute(s -> doSend(user, r, amount, key, hash));
  }

  private Receipt doSend(
    UUID user,
    Request r,
    BigDecimal amount,
    String key,
    String hash
  ) {
    Receipt previous = replay(user, key, hash); // otra petición pudo ganar la carrera
    if (previous != null) return previous;

    UUID destinationId = accounts
      .findIdByAccountNumber(r.destinationAccountNumber())
      .orElseThrow(() -> bad("Destination not found"));
    if (r.sourceAccountId().equals(destinationId)) throw bad("Same account");

    Map<UUID, Account> locked = lock(r.sourceAccountId(), destinationId);
    Account source = locked.get(r.sourceAccountId());
    if (!source.ownerId.equals(user)) throw status(
      HttpStatus.FORBIDDEN,
      "Not your account"
    );
    Transfer t = apply(
      source,
      locked.get(destinationId),
      amount,
      r.reference(),
      "TRANSFER",
      null
    );
    keys.saveAndFlush(new IdempotencyKey(user, key, hash, t.id));
    return receipt(t);
  }

  /** Reembolsa (total o parcialmente) una transferencia. Solo quien la recibió puede devolverla. */
  @Transactional
  public Receipt refund(UUID user, UUID transferId, BigDecimal amount, String key) {
    requireKey(key);
    Transfer original = transfers
      .findById(transferId)
      .orElseThrow(() -> status(HttpStatus.NOT_FOUND, "Transfer not found"));
    if (!"TRANSFER".equals(original.type)) throw bad(
      "Only transfers can be refunded"
    );
    String hash = hash("REFUND|" + transferId + "|" + amount);
    Receipt previous = replay(user, key, hash);
    if (previous != null) return previous;

    // Bloquear las cuentas serializa los reembolsos concurrentes de la misma transferencia.
    Map<UUID, Account> locked = lock(original.recipientId, original.senderId);
    Account payer = locked.get(original.recipientId);
    if (!payer.ownerId.equals(user)) throw status(
      HttpStatus.FORBIDDEN,
      "Not your account"
    );
    BigDecimal alreadyRefunded = transfers.totalRefunded(original.id);
    BigDecimal remaining = original.amount.subtract(alreadyRefunded);
    BigDecimal toRefund = amount == null ? remaining : Money.valid(amount);
    if (toRefund.compareTo(remaining) > 0) throw bad(
      "Refund exceeds the refundable amount (" + remaining + ")"
    );
    if (toRefund.signum() <= 0) throw bad("Nothing left to refund");

    Transfer refund = apply(
      payer,
      locked.get(original.senderId),
      toRefund,
      "Refund of " + original.id,
      "REFUND",
      original.id
    );
    original.status = toRefund.compareTo(remaining) == 0
      ? "REFUNDED"
      : "PARTIALLY_REFUNDED";
    keys.saveAndFlush(new IdempotencyKey(user, key, hash, refund.id));
    return receipt(refund);
  }

  @Transactional(readOnly = true)
  public List<Transfer> mine(UUID user) {
    List<UUID> ids = accounts
      .findByOwnerId(user)
      .stream()
      .map(a -> a.id)
      .toList();
    if (ids.isEmpty()) return List.of();
    return transfers.findTop30BySenderIdInOrRecipientIdInOrderByCreatedAtDesc(
      ids,
      ids
    );
  }

  // ---- piezas compartidas por send() y refund() ----

  /** Mueve el dinero y deja el rastro contable. Las cuentas ya vienen bloqueadas. */
  private Transfer apply(
    Account from,
    Account to,
    BigDecimal amount,
    String reference,
    String type,
    UUID originalId
  ) {
    if (!from.status.equals("ACTIVE") || !to.status.equals("ACTIVE")) throw bad(
      "Account inactive"
    );
    if (!from.currency.equals(to.currency)) throw bad("Currency mismatch");
    if (from.balance.compareTo(amount) < 0) throw bad("Insufficient funds");

    from.balance = from.balance.subtract(amount);
    to.balance = to.balance.add(amount);
    return record(from.id, to.id, amount, reference, type, originalId);
  }

  /**
   * Recarga de saldo (pasarela de pagos). El dinero viene del "mundo exterior": se acredita
   * la cuenta y el ledger registra DEBIT en la cuenta externa + CREDIT en la del usuario.
   * Debe llamarse dentro de una transacción.
   */
  public void topUp(UUID accountId, BigDecimal amount, String reference) {
    Account to = accounts
      .lockById(accountId)
      .orElseThrow(() -> bad("Account not found"));
    to.balance = to.balance.add(amount);
    record(EXTERNAL_ACCOUNT, to.id, amount, reference, "TOPUP", null);
  }

  /** Rastro contable común: transferencia + asientos de doble entrada + evento outbox. */
  private Transfer record(
    UUID from,
    UUID to,
    BigDecimal amount,
    String reference,
    String type,
    UUID originalId
  ) {
    Transfer t = transfers.save(
      new Transfer(from, to, amount, reference, type, originalId)
    );
    ledger.save(new LedgerEntry(t.id, from, "DEBIT", amount));
    ledger.save(new LedgerEntry(t.id, to, "CREDIT", amount));
    outbox.add(
      switch (type) {
        case "REFUND" -> "RefundCompleted";
        case "TOPUP" -> "TopUpCompleted";
        default -> "TransferCompleted";
      },
      Map.of("transferId", t.id, "from", from, "to", to, "amount", amount)
    );
    return t;
  }

  /** Bloqueo pesimista de ambas cuentas, siempre en orden de id. */
  private Map<UUID, Account> lock(UUID a, UUID b) {
    Map<UUID, Account> locked = new HashMap<>();
    for (UUID id : new TreeSet<>(List.of(a, b))) {
      locked.put(
        id,
        accounts.lockById(id).orElseThrow(() -> bad("Account not found"))
      );
    }
    return locked;
  }

  /** Si la clave ya se usó: devuelve el resultado original o 409 si el contenido cambió. */
  private Receipt replay(UUID user, String key, String hash) {
    return keys
      .findByUserIdAndRequestKey(user, key)
      .map(k -> {
        if (!k.requestHash.equals(hash)) throw status(
          HttpStatus.CONFLICT,
          "Idempotency key reused with different payload"
        );
        return receipt(transfers.findById(k.transferId).orElseThrow());
      })
      .orElse(null);
  }

  private void requireKey(String key) {
    if (key == null || key.isBlank() || key.length() > 100) throw bad(
      "Idempotency-Key required (max 100)"
    );
  }

  private Receipt receipt(Transfer t) {
    return new Receipt(t.id, t.status, t.amount, t.reference);
  }

  private ResponseStatusException bad(String message) {
    return status(HttpStatus.BAD_REQUEST, message);
  }

  private ResponseStatusException status(HttpStatus s, String message) {
    return new ResponseStatusException(s, message);
  }

  private String hash(String s) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(
        s.getBytes(StandardCharsets.UTF_8)
      );
      return HexFormat.of().formatHex(digest);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}

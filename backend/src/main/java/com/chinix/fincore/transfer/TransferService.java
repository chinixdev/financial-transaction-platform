package com.chinix.fincore.transfer;

import com.chinix.fincore.account.*;
import com.chinix.fincore.auth.AuthService;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TransferService {

  private final AccountRepository accounts;
  private final TransferRepository transfers;
  private final LedgerRepository ledger;
  private final IdempotencyRepository keys;
  private final AuthService auth;

  public TransferService(
    AccountRepository accounts,
    TransferRepository transfers,
    LedgerRepository ledger,
    IdempotencyRepository keys,
    AuthService auth
  ) {
    this.accounts = accounts;
    this.transfers = transfers;
    this.ledger = ledger;
    this.keys = keys;
    this.auth = auth;
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

  @Transactional
  public Receipt send(Request r, String key) {
    UUID user = auth.currentUserId();
    if (key == null || key.isBlank() || key.length() > 100) throw bad(
      "Idempotency-Key required (max 100)"
    );
    if (
      r.amount() == null ||
      r.amount().scale() > 2 ||
      r.amount().compareTo(BigDecimal.ZERO) <= 0
    ) throw bad("Invalid amount");
    if (
      r.sourceAccountId() == null || r.destinationAccountNumber() == null
    ) throw bad("Account required");
    String hash = hash(
      r.sourceAccountId() +
        "|" +
        r.destinationAccountNumber() +
        "|" +
        r.amount().setScale(2) +
        "|" +
        Objects.toString(r.reference(), "")
    );
    Optional<IdempotencyKey> previous = keys.findByUserIdAndRequestKey(
      user,
      key
    );
    if (previous.isPresent()) {
      IdempotencyKey k = previous.get();
      if (!k.requestHash.equals(hash)) throw new ResponseStatusException(
        HttpStatus.CONFLICT,
        "Idempotency key reused with different payload"
      );
      return receipt(transfers.findById(k.transferId).orElseThrow());
    }
    Account destination = accounts
      .findByAccountNumber(r.destinationAccountNumber())
      .orElseThrow(() -> bad("Destination not found"));
    if (r.sourceAccountId().equals(destination.id)) throw bad("Same account");
    // Stable lock order prevents deadlocks for opposite-direction transfers.
    UUID first =
      r.sourceAccountId().compareTo(destination.id) < 0
        ? r.sourceAccountId()
        : destination.id;
    UUID second = first.equals(r.sourceAccountId())
      ? destination.id
      : r.sourceAccountId();
    Account a = accounts
      .lockById(first)
      .orElseThrow(() -> bad("Source not found"));
    Account b = accounts
      .lockById(second)
      .orElseThrow(() -> bad("Account not found"));
    Account source = a.id.equals(r.sourceAccountId()) ? a : b;
    Account target = a.id.equals(destination.id) ? a : b;
    if (!source.ownerId.equals(user)) throw new ResponseStatusException(
      HttpStatus.FORBIDDEN,
      "Not your account"
    );
    if (
      !source.status.equals("ACTIVE") || !target.status.equals("ACTIVE")
    ) throw bad("Account inactive");
    if (!source.currency.equals(target.currency)) throw bad(
      "Currency mismatch"
    );
    if (source.balance.compareTo(r.amount()) < 0) throw bad(
      "Insufficient funds"
    );
    source.balance = source.balance.subtract(r.amount());
    target.balance = target.balance.add(r.amount());
    Transfer t = transfers.save(
      new Transfer(source.id, target.id, r.amount().setScale(2), r.reference())
    );
    ledger.save(new LedgerEntry(t.id, source.id, "DEBIT", r.amount()));
    ledger.save(new LedgerEntry(t.id, target.id, "CREDIT", r.amount()));
    keys.saveAndFlush(new IdempotencyKey(user, key, hash, t.id));
    return receipt(t);
  }

  private Receipt receipt(Transfer t) {
    return new Receipt(t.id, t.status, t.amount, t.reference);
  }

  private ResponseStatusException bad(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
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

  @Transactional(readOnly = true)
  public List<Transfer> mine() {
    List<UUID> ids = accounts
      .findByOwnerId(auth.currentUserId())
      .stream()
      .map(a -> a.id)
      .toList();
    if (ids.isEmpty()) return List.of();
    return transfers.findTop30BySenderIdInOrRecipientIdInOrderByCreatedAtDesc(
      ids,
      ids
    );
  }
}

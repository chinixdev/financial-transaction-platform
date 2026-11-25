package com.chinix.fincore.transfer;

import com.chinix.fincore.account.AccountRepository;
import com.chinix.fincore.auth.AuthService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ledger")
public class LedgerController {

  private final LedgerRepository ledger;
  private final AccountRepository accounts;
  private final AuthService auth;

  public LedgerController(
    LedgerRepository ledger,
    AccountRepository accounts,
    AuthService auth
  ) {
    this.ledger = ledger;
    this.accounts = accounts;
    this.auth = auth;
  }

  public record EntryView(
    UUID id,
    UUID transferId,
    UUID accountId,
    String direction,
    BigDecimal amount,
    Instant createdAt
  ) {}

  /** Chequeo de integridad: en doble entrada, débitos y créditos deben sumar lo mismo. */
  public record Verification(
    BigDecimal debits,
    BigDecimal credits,
    boolean balanced
  ) {}

  @GetMapping
  public List<EntryView> mine() {
    List<UUID> ids = accounts
      .findByOwnerId(auth.currentUserId())
      .stream()
      .map(a -> a.id)
      .toList();
    if (ids.isEmpty()) return List.of();
    return ledger
      .findTop50ByAccountIdInOrderByCreatedAtDesc(ids)
      .stream()
      .map(e ->
        new EntryView(
          e.id,
          e.transferId,
          e.accountId,
          e.direction,
          e.amount,
          e.createdAt
        )
      )
      .toList();
  }

  @GetMapping("/verify")
  public Verification verify() {
    BigDecimal debits = ledger.total("DEBIT");
    BigDecimal credits = ledger.total("CREDIT");
    return new Verification(debits, credits, debits.compareTo(credits) == 0);
  }
}

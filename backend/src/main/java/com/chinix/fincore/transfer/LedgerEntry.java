package com.chinix.fincore.transfer;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

  @Id
  public UUID id;

  @Column(name = "transfer_id")
  public UUID transferId;

  @Column(name = "account_id")
  public UUID accountId;

  public String direction;
  public BigDecimal amount;

  protected LedgerEntry() {}

  public LedgerEntry(
    UUID transferId,
    UUID accountId,
    String direction,
    BigDecimal amount
  ) {
    id = UUID.randomUUID();
    this.transferId = transferId;
    this.accountId = accountId;
    this.direction = direction;
    this.amount = amount;
  }
}

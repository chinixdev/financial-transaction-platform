package com.chinix.fincore.payment;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

  @Id
  public UUID id;

  @Column(name = "user_id")
  public UUID userId;

  @Column(name = "account_id")
  public UUID accountId;

  public BigDecimal amount;
  public String currency;
  public String provider;

  @Column(name = "provider_ref")
  public String providerRef;

  /** PENDING -> SUCCEEDED o FAILED. */
  public String status = "PENDING";

  @Column(name = "idempotency_key")
  public String idempotencyKey;

  @Column(name = "created_at", insertable = false, updatable = false)
  public Instant createdAt;

  protected Payment() {}

  public Payment(
    UUID userId,
    UUID accountId,
    BigDecimal amount,
    String provider,
    String key
  ) {
    this.id = UUID.randomUUID();
    this.userId = userId;
    this.accountId = accountId;
    this.amount = amount;
    this.currency = "PEN";
    this.provider = provider;
    this.idempotencyKey = key;
  }
}

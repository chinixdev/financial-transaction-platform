package com.chinix.fincore.fraud;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fraud_alerts")
public class FraudAlert {

  @Id
  public UUID id;

  @Column(name = "user_id")
  public UUID userId;

  @Column(name = "source_account_id")
  public UUID sourceAccountId;

  public BigDecimal amount;
  public int score;

  /** LOW, MEDIUM o HIGH. */
  public String level;

  /** ALLOWED o BLOCKED. */
  public String decision;

  public String reasons;

  @Column(name = "created_at", insertable = false, updatable = false)
  public Instant createdAt;

  protected FraudAlert() {}

  public FraudAlert(
    UUID userId,
    UUID sourceAccountId,
    BigDecimal amount,
    FraudEngine.Decision d
  ) {
    this.id = UUID.randomUUID();
    this.userId = userId;
    this.sourceAccountId = sourceAccountId;
    this.amount = amount;
    this.score = d.score();
    this.level = d.level();
    this.decision = d.blocked() ? "BLOCKED" : "ALLOWED";
    this.reasons = String.join(",", d.reasons());
  }
}

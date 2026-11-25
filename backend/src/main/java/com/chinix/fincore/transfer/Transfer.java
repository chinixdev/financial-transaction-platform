package com.chinix.fincore.transfer;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfers")
public class Transfer {

  @Id
  public UUID id;

  @Column(name = "sender_id")
  public UUID senderId;

  @Column(name = "recipient_id")
  public UUID recipientId;

  public BigDecimal amount;
  public String status;
  public String reference;

  @Column(name = "created_at", insertable = false, updatable = false)
  public Instant createdAt;

  protected Transfer() {}

  public Transfer(UUID from, UUID to, BigDecimal amount, String reference) {
    id = UUID.randomUUID();
    senderId = from;
    recipientId = to;
    this.amount = amount;
    this.reference = reference;
    status = "COMPLETED";
  }
}

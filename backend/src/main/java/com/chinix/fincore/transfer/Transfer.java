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

  /** COMPLETED, PARTIALLY_REFUNDED o REFUNDED. */
  public String status;

  public String reference;

  /** TRANSFER o REFUND. */
  public String type;

  /** Solo para reembolsos: la transferencia original. */
  @Column(name = "original_transfer_id")
  public UUID originalTransferId;

  @Column(name = "created_at", insertable = false, updatable = false)
  public Instant createdAt;

  protected Transfer() {}

  public Transfer(
    UUID from,
    UUID to,
    BigDecimal amount,
    String reference,
    String type,
    UUID originalTransferId
  ) {
    id = UUID.randomUUID();
    senderId = from;
    recipientId = to;
    this.amount = amount;
    this.reference = reference;
    this.type = type;
    this.originalTransferId = originalTransferId;
    status = "COMPLETED";
  }
}

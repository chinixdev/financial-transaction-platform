package com.chinix.fincore.transfer;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(
  name = "idempotency_keys",
  uniqueConstraints = @UniqueConstraint(
    columnNames = { "user_id", "request_key" }
  )
)
public class IdempotencyKey {

  @Id
  public UUID id;

  @Column(name = "user_id")
  public UUID userId;

  @Column(name = "request_key")
  public String requestKey;

  @Column(name = "request_hash")
  public String requestHash;

  @Column(name = "transfer_id")
  public UUID transferId;

  protected IdempotencyKey() {}

  public IdempotencyKey(UUID userId, String key, String hash, UUID transferId) {
    id = UUID.randomUUID();
    this.userId = userId;
    requestKey = key;
    requestHash = hash;
    this.transferId = transferId;
  }
}

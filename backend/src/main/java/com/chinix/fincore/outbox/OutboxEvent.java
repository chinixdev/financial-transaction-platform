package com.chinix.fincore.outbox;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

  @Id
  public UUID id;

  public String type;
  public String payload;

  /** PENDING -> PUBLISHED, o FAILED si se agotan los reintentos. */
  public String status = "PENDING";

  public int attempts;

  @Column(name = "created_at", insertable = false, updatable = false)
  public Instant createdAt;

  @Column(name = "processed_at")
  public Instant processedAt;

  protected OutboxEvent() {}

  public OutboxEvent(String type, String payload) {
    this.id = UUID.randomUUID();
    this.type = type;
    this.payload = payload;
  }
}

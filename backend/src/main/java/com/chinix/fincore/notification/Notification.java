package com.chinix.fincore.notification;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

  @Id
  public UUID id;

  @Column(name = "user_id")
  public UUID userId;

  /** Id del evento del outbox que la originó (con user_id es UNIQUE: consumo idempotente). */
  @Column(name = "event_id")
  public UUID eventId;

  public String type;
  public String message;

  @Column(name = "created_at", insertable = false, updatable = false)
  public Instant createdAt;

  protected Notification() {}

  public Notification(UUID userId, UUID eventId, String type, String message) {
    this.id = UUID.randomUUID();
    this.userId = userId;
    this.eventId = eventId;
    this.type = type;
    this.message = message;
  }
}

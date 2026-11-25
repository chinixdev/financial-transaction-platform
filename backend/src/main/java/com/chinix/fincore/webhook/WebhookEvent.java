package com.chinix.fincore.webhook;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_events")
public class WebhookEvent {

  @Id
  public UUID id;

  public String provider;

  /** Id del evento en el proveedor (UNIQUE): garantiza procesarlo una sola vez. */
  @Column(name = "event_id")
  public String eventId;

  public String type;

  /** PROCESSED o IGNORED (tipo de evento que no manejamos). */
  public String status;

  @Column(name = "payment_id")
  public UUID paymentId;

  @Column(name = "received_at", insertable = false, updatable = false)
  public Instant receivedAt;

  protected WebhookEvent() {}

  public WebhookEvent(String provider, String eventId, String type) {
    this.id = UUID.randomUUID();
    this.provider = provider;
    this.eventId = eventId;
    this.type = type;
  }
}

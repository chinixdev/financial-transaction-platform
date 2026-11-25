package com.chinix.fincore.notification;

import com.chinix.fincore.outbox.OutboxEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor en proceso (modo local, sin broker). Si falla, el OutboxPublisher reintenta y,
 * tras 5 intentos, deja el evento en FAILED (cola de errores).
 * Con app.rabbitmq.enabled=true lo reemplaza el consumidor de RabbitMQ.
 */
@Component
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "false", matchIfMissing = true)
public class NotificationListener {

  private final NotificationService service;

  public NotificationListener(NotificationService service) {
    this.service = service;
  }

  @EventListener
  public void on(OutboxEvent event) {
    service.handle(event.id, event.type, event.payload);
  }
}

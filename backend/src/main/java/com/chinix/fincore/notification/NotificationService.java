package com.chinix.fincore.notification;

import com.chinix.fincore.account.AccountRepository;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.RoundingMode;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumidor de eventos del outbox: crea notificaciones para las personas involucradas.
 * Es idempotente: procesar dos veces el mismo evento no duplica nada (UNIQUE event_id + user_id).
 * Lo invoca el listener en proceso o, si RabbitMQ está activo, el consumidor de la cola.
 */
@Service
public class NotificationService {

  /** Usuario "sistema" (dueño de la cuenta externa): no recibe notificaciones. */
  private static final UUID SYSTEM_USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

  private final NotificationRepository repo;
  private final AccountRepository accounts;
  private final ObjectMapper json;

  public NotificationService(
    NotificationRepository repo,
    AccountRepository accounts,
    ObjectMapper json
  ) {
    this.repo = repo;
    this.accounts = accounts;
    this.json = json;
  }

  @Transactional
  public void handle(UUID eventId, String type, String payload) {
    JsonNode event;
    try {
      // BigDecimal (no double): un importe nunca debe pasar por coma flotante.
      event = json
        .readerFor(JsonNode.class)
        .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        .readTree(payload);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid event payload", e);
    }
    String amount = event
      .path("amount")
      .decimalValue()
      .setScale(2, RoundingMode.HALF_UP)
      .toPlainString();
    switch (type) {
      case "TransferCompleted" -> {
        notify(eventId, event.path("from"), type, "Enviaste S/ " + amount);
        notify(eventId, event.path("to"), type, "Recibiste S/ " + amount);
      }
      case "RefundCompleted" -> {
        notify(eventId, event.path("from"), type, "Reembolso enviado: S/ " + amount);
        notify(eventId, event.path("to"), type, "Reembolso recibido: S/ " + amount);
      }
      case "TopUpCompleted" -> notify(eventId, event.path("to"), type, "Recarga acreditada: S/ " + amount);
      default -> {} // tipos que aún no generan notificación
    }
  }

  private void notify(UUID eventId, JsonNode accountId, String type, String message) {
    if (accountId.isMissingNode()) return;
    accounts
      .findById(UUID.fromString(accountId.asText()))
      .map(a -> a.ownerId)
      .filter(user -> !user.equals(SYSTEM_USER))
      .filter(user -> !repo.existsByEventIdAndUserId(eventId, user))
      .ifPresent(user -> repo.save(new Notification(user, eventId, type, message)));
  }
}

package com.chinix.fincore.webhook;

import com.chinix.fincore.payment.PaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Recibe eventos del proveedor de pagos. Orden: (1) verificar firma, (2) descartar duplicados
 * por event_id, (3) procesar y registrar el evento en la MISMA transacción: si algo falla,
 * no queda registrado y el proveedor puede reintentar.
 */
@Service
public class WebhookService {

  public enum Result {
    PROCESSED,
    DUPLICATE,
  }

  private final WebhookEventRepository events;
  private final PaymentService payments;
  private final ObjectMapper json;
  private final TransactionTemplate tx;
  private final String secret;

  public WebhookService(
    WebhookEventRepository events,
    PaymentService payments,
    ObjectMapper json,
    TransactionTemplate tx,
    @Value("${app.stripe.webhook-secret}") String secret
  ) {
    this.events = events;
    this.payments = payments;
    this.json = json;
    this.tx = tx;
    this.secret = secret;
  }

  public String secret() {
    return secret;
  }

  public Result handle(String payload, String signatureHeader) {
    if (
      !StripeSignature.valid(payload, signatureHeader, secret, Instant.now().getEpochSecond())
    ) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid signature");
    }
    JsonNode event;
    try {
      event = json.readTree(payload);
    } catch (Exception e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payload");
    }
    String eventId = event.path("id").asText("");
    String type = event.path("type").asText("");
    String reference = event.path("data").path("object").path("id").asText("");
    if (eventId.isBlank() || type.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payload");
    }
    if (events.existsByEventId(eventId)) return Result.DUPLICATE;

    try {
      return tx.execute(s -> {
        WebhookEvent record = new WebhookEvent("STRIPE", eventId, type);
        UUID paymentId = switch (type) {
          case "payment_intent.succeeded" -> payments.complete(reference);
          case "payment_intent.payment_failed" -> payments.fail(reference);
          default -> null;
        };
        record.paymentId = paymentId;
        record.status = paymentId == null ? "IGNORED" : "PROCESSED";
        events.saveAndFlush(record);
        return Result.PROCESSED;
      });
    } catch (DataIntegrityViolationException e) {
      return Result.DUPLICATE; // dos entregas simultáneas del mismo evento: gana una
    }
  }
}

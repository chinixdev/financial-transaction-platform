package com.chinix.fincore.webhook;

import com.chinix.fincore.auth.AuthService;
import com.chinix.fincore.payment.PaymentService;
import java.time.Instant;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

  public record EventView(
    UUID id,
    String eventId,
    String type,
    String status,
    UUID paymentId,
    Instant receivedAt
  ) {}

  private final WebhookService service;
  private final WebhookEventRepository events;
  private final PaymentService payments;
  private final AuthService auth;

  public WebhookController(
    WebhookService service,
    WebhookEventRepository events,
    PaymentService payments,
    AuthService auth
  ) {
    this.service = service;
    this.events = events;
    this.payments = payments;
    this.auth = auth;
  }

  /** Pública (la llama el proveedor): se protege con la firma, no con JWT. */
  @PostMapping("/stripe")
  public Map<String, String> receive(
    @RequestBody String payload,
    @RequestHeader(value = "Stripe-Signature", required = false) String signature
  ) {
    return Map.of("result", service.handle(payload, signature).name());
  }

  /** Eventos asociados a los pagos del usuario autenticado. */
  @GetMapping("/events")
  public List<EventView> mine() {
    List<UUID> paymentIds = payments
      .mine(auth.currentUserId())
      .stream()
      .map(p -> p.id)
      .toList();
    if (paymentIds.isEmpty()) return List.of();
    return events
      .findTop50ByPaymentIdInOrderByReceivedAtDesc(paymentIds)
      .stream()
      .map(e -> new EventView(e.id, e.eventId, e.type, e.status, e.paymentId, e.receivedAt))
      .toList();
  }
}

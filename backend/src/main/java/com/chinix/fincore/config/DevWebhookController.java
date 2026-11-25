package com.chinix.fincore.config;

import com.chinix.fincore.payment.*;
import com.chinix.fincore.webhook.*;
import java.time.Instant;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * SOLO perfil "local": simula que el proveedor envía el webhook "payment_intent.succeeded"
 * de un pago propio (con firma válida), para probar el flujo sin Stripe ni túneles públicos.
 * El id del evento depende del pago: pulsar dos veces demuestra la deduplicación.
 */
@RestController
@Profile("local")
@RequestMapping("/api/dev")
public class DevWebhookController {

  private final PaymentRepository payments;
  private final WebhookService webhooks;
  private final com.chinix.fincore.auth.AuthService auth;

  public DevWebhookController(
    PaymentRepository payments,
    WebhookService webhooks,
    com.chinix.fincore.auth.AuthService auth
  ) {
    this.payments = payments;
    this.webhooks = webhooks;
    this.auth = auth;
  }

  @PostMapping("/payments/{id}/simulate-webhook")
  public Map<String, String> simulate(@PathVariable UUID id) {
    Payment p = payments
      .findById(id)
      .filter(x -> x.userId.equals(auth.currentUserId()))
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
    String payload =
      "{\"id\":\"evt_dev_" + p.id + "\",\"type\":\"payment_intent.succeeded\"," +
      "\"data\":{\"object\":{\"id\":\"" + p.providerRef + "\"}}}";
    String header = StripeSignature.header(payload, webhooks.secret(), Instant.now().getEpochSecond());
    return Map.of("result", webhooks.handle(payload, header).name());
  }
}

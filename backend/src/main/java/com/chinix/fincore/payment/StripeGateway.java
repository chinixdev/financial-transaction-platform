package com.chinix.fincore.payment;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Stripe en MODO PRUEBA (clave sk_test_...). Usa la API REST directamente, sin SDK.
 * Crea un PaymentIntent con la tarjeta de prueba pm_card_visa; no se maneja ningún
 * dato de tarjeta real.
 */
public class StripeGateway implements PaymentGateway {

  private final RestClient http;
  private final String currency;

  public StripeGateway(
    String secretKey,
    String currency,
    RestClient.Builder builder
  ) {
    this.currency = currency;
    this.http = builder
      .baseUrl("https://api.stripe.com")
      .defaultHeader("Authorization", "Bearer " + secretKey)
      .build();
  }

  @Override
  public String name() {
    return "STRIPE";
  }

  @Override
  public Charge createCharge(
    BigDecimal amount,
    String ignored,
    String idempotencyKey
  ) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    // Stripe trabaja en la unidad mínima (centavos).
    form.add("amount", String.valueOf(amount.movePointRight(2).longValueExact()));
    form.add("currency", currency);
    form.add("payment_method", "pm_card_visa"); // tarjeta de prueba de Stripe
    form.add("confirm", "true");
    form.add("automatic_payment_methods[enabled]", "true");
    form.add("automatic_payment_methods[allow_redirects]", "never");

    JsonNode body = http
      .post()
      .uri("/v1/payment_intents")
      .header("Idempotency-Key", idempotencyKey)
      .contentType(MediaType.APPLICATION_FORM_URLENCODED)
      .body(form)
      .retrieve()
      .body(JsonNode.class);
    return new Charge(body.get("id").asText(), body.get("status").asText());
  }

  /** Últimos 100 PaymentIntents (suficiente para la conciliación de pruebas; sin paginar). */
  @Override
  public List<ProviderCharge> listCharges() {
    JsonNode body = http
      .get()
      .uri("/v1/payment_intents?limit=100")
      .retrieve()
      .body(JsonNode.class);
    List<ProviderCharge> result = new ArrayList<>();
    for (JsonNode pi : body.get("data")) {
      result.add(
        new ProviderCharge(
          pi.get("id").asText(),
          BigDecimal.valueOf(pi.get("amount").asLong(), 2),
          pi.get("status").asText()
        )
      );
    }
    return result;
  }
}

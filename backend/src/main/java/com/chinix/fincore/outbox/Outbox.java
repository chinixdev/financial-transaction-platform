package com.chinix.fincore.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Guarda un evento en la tabla outbox. Debe llamarse dentro de la transacción de negocio. */
@Component
public class Outbox {

  private final OutboxRepository repo;
  private final ObjectMapper json;

  public Outbox(OutboxRepository repo, ObjectMapper json) {
    this.repo = repo;
    this.json = json;
  }

  public void add(String type, Object payload) {
    try {
      repo.save(new OutboxEvent(type, json.writeValueAsString(payload)));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }
}

package com.chinix.fincore.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Lee los eventos PENDING y los publica. Si la publicación falla se reintenta en la siguiente
 * vuelta; tras MAX_ATTEMPTS el evento pasa a FAILED (cola de errores).
 *
 * Cada evento va en SU PROPIA transacción: si un consumidor falla, solo se deshace ese evento
 * (con sus efectos) y el contador de intentos se guarda aparte. Con una única transacción para
 * todo el lote, un evento defectuoso arrastraba al resto y nunca llegaba a FAILED.
 */
@Component
public class OutboxPublisher {

  public static final int MAX_ATTEMPTS = 5;
  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

  private final OutboxRepository repo;
  private final ApplicationEventPublisher bus;
  private final TransactionTemplate tx;

  public OutboxPublisher(
    OutboxRepository repo,
    ApplicationEventPublisher bus,
    TransactionTemplate tx
  ) {
    this.repo = repo;
    this.bus = bus;
    this.tx = tx;
  }

  @Scheduled(fixedDelayString = "${app.outbox.delay-ms:2000}")
  public void publishPending() {
    for (OutboxEvent pending : repo.findTop50ByStatusOrderByCreatedAt("PENDING")) {
      publishOne(pending.id);
    }
  }

  private void publishOne(UUID id) {
    try {
      tx.executeWithoutResult(s -> {
        OutboxEvent e = repo.findById(id).orElseThrow();
        bus.publishEvent(e); // los consumidores escriben dentro de esta transacción
        e.status = "PUBLISHED";
        e.processedAt = Instant.now();
      });
    } catch (RuntimeException ex) {
      tx.executeWithoutResult(s -> {
        OutboxEvent e = repo.findById(id).orElseThrow();
        e.attempts++;
        if (e.attempts >= MAX_ATTEMPTS) e.status = "FAILED";
        log.warn("Outbox {} falló (intento {})", id, e.attempts, ex);
      });
    }
  }

  /** Cola de errores: devuelve los eventos FAILED a PENDING (p. ej. tras arreglar el consumidor). */
  @Transactional
  public int requeueFailed() {
    List<OutboxEvent> failed = repo.findByStatus("FAILED");
    for (OutboxEvent e : failed) {
      e.status = "PENDING";
      e.attempts = 0;
    }
    return failed.size();
  }
}

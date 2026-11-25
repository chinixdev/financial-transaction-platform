package com.chinix.fincore;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.chinix.fincore.outbox.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Prueba unitaria (Mockito): recuperación de eventos fallidos. La transacción real se prueba en NotificationTest. */
class OutboxPublisherTest {

  OutboxRepository repo = mock(OutboxRepository.class);
  ApplicationEventPublisher bus = mock(ApplicationEventPublisher.class);
  OutboxPublisher publisher = new OutboxPublisher(
    repo,
    bus,
    new TransactionTemplate(mock(PlatformTransactionManager.class))
  );

  OutboxEvent pendingEvent() {
    OutboxEvent e = new OutboxEvent("TransferCompleted", "{}");
    when(repo.findTop50ByStatusOrderByCreatedAt("PENDING")).thenReturn(List.of(e));
    when(repo.findById(e.id)).thenReturn(Optional.of(e));
    return e;
  }

  @Test
  void successfulPublishMarksEventAsPublished() {
    OutboxEvent e = pendingEvent();

    publisher.publishPending();

    assertEquals("PUBLISHED", e.status);
    assertNotNull(e.processedAt);
  }

  @Test
  void failedPublishIsRetriedAndMovesToFailedAfterMaxAttempts() {
    OutboxEvent e = pendingEvent();
    doThrow(new RuntimeException("broker caído")).when(bus).publishEvent(any(Object.class));

    publisher.publishPending();
    assertEquals("PENDING", e.status); // sigue pendiente: se reintentará
    assertEquals(1, e.attempts);

    for (int i = 1; i < OutboxPublisher.MAX_ATTEMPTS; i++) publisher.publishPending();
    assertEquals("FAILED", e.status);
  }

  @Test
  void eventPublishedAfterTransientFailureIsDelivered() {
    OutboxEvent e = pendingEvent();
    doThrow(new RuntimeException("fallo temporal"))
      .doNothing()
      .when(bus)
      .publishEvent(any(Object.class));

    publisher.publishPending();
    publisher.publishPending();

    assertEquals("PUBLISHED", e.status);
  }
}

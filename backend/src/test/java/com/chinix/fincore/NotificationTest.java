package com.chinix.fincore;

import static org.junit.jupiter.api.Assertions.*;

import com.chinix.fincore.notification.*;
import com.chinix.fincore.outbox.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Consumo de eventos del outbox: notificaciones idempotentes y cola de errores. */
@SpringBootTest(
  properties = {
    "app.outbox.delay-ms=3600000",
    "app.fraud.max-per-window=100000",
  }
)
class NotificationTest extends TestBase {

  @Autowired NotificationRepository notifications;
  @Autowired NotificationService notificationService;
  @Autowired OutboxPublisher publisher;
  @Autowired OutboxRepository outbox;

  @Test
  void publishedTransferNotifiesBothParties() {
    Wallet a = wallet("100.00"), b = wallet("0.00");
    send(a, b, "25.00", key());

    drainOutbox();

    assertTrue(
      notifications.findTop50ByUserIdOrderByCreatedAtDesc(a.user()).stream().anyMatch(n -> n.message.equals("Enviaste S/ 25.00"))
    );
    assertTrue(
      notifications.findTop50ByUserIdOrderByCreatedAtDesc(b.user()).stream().anyMatch(n -> n.message.equals("Recibiste S/ 25.00"))
    );
  }

  @Test
  void deliveringTheSameEventTwiceDoesNotDuplicateNotifications() {
    Wallet a = wallet("100.00"), b = wallet("0.00");
    send(a, b, "10.00", key());
    UUID eventId = UUID.randomUUID();
    String payload =
      "{\"from\":\"" + a.accountId() + "\",\"to\":\"" + b.accountId() + "\",\"amount\":10.00}";

    notificationService.handle(eventId, "TransferCompleted", payload);
    notificationService.handle(eventId, "TransferCompleted", payload); // reentrega

    long forB = notifications
      .findTop50ByUserIdOrderByCreatedAtDesc(b.user())
      .stream()
      .filter(n -> n.eventId.equals(eventId))
      .count();
    assertEquals(1, forB);
  }

  @Test
  void malformedEventFailsSoItCanBeRetriedOrDeadLettered() {
    assertThrows(
      IllegalArgumentException.class,
      () -> notificationService.handle(UUID.randomUUID(), "TransferCompleted", "no es json")
    );
  }

  @Test
  void poisonEventDoesNotBlockOthersAndEndsInFailed() {
    OutboxEvent poison = outbox.save(new OutboxEvent("TransferCompleted", "no es json"));
    Wallet a = wallet("100.00"), b = wallet("0.00");
    send(a, b, "7.00", key()); // evento sano, creado DESPUÉS del venenoso

    for (int i = 0; i < 200 && outbox.findById(poison.id).orElseThrow().attempts < OutboxPublisher.MAX_ATTEMPTS; i++) {
      publisher.publishPending();
    }

    // El venenoso agotó sus intentos y quedó en la cola de errores...
    assertEquals("FAILED", outbox.findById(poison.id).orElseThrow().status);
    // ...y no impidió que el evento sano notificara.
    assertTrue(
      notifications.findTop50ByUserIdOrderByCreatedAtDesc(b.user()).stream().anyMatch(n -> n.message.equals("Recibiste S/ 7.00"))
    );
  }

  @Test
  void failedEventsCanBeRequeuedFromTheDeadLetterQueue() {
    OutboxEvent dead = new OutboxEvent("TransferCompleted", "{}");
    dead.status = "FAILED";
    dead.attempts = OutboxPublisher.MAX_ATTEMPTS;
    outbox.save(dead);

    int requeued = publisher.requeueFailed();

    assertTrue(requeued >= 1);
    OutboxEvent after = outbox.findById(dead.id).orElseThrow();
    assertEquals("PENDING", after.status);
    assertEquals(0, after.attempts);
  }

  /** El publicador procesa lotes de 50; la base es compartida, así que se repite hasta vaciarla. */
  void drainOutbox() {
    for (int i = 0; i < 200 && outbox.countByStatus("PENDING") > 0; i++) {
      publisher.publishPending();
    }
  }
}

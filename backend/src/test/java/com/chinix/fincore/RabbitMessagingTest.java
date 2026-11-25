package com.chinix.fincore;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.chinix.fincore.messaging.*;
import com.chinix.fincore.notification.NotificationService;
import com.chinix.fincore.outbox.OutboxEvent;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Lógica de mensajería probada con Mockito. NO se probó contra un broker RabbitMQ real
 * (no hay uno instalado): la topología y los reintentos hacia la DLQ son declarativos.
 */
class RabbitMessagingTest {

  @Test
  void forwarderPublishesTheOutboxEventWithItsIdAndTypeAsRoutingKey() {
    RabbitTemplate rabbit = mock(RabbitTemplate.class);
    OutboxEvent event = new OutboxEvent("TransferCompleted", "{\"amount\":5}");

    new RabbitForwarder(rabbit).on(event);

    ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
    verify(rabbit).send(eq(RabbitConfig.EXCHANGE), eq("TransferCompleted"), sent.capture());
    assertEquals(event.id.toString(), sent.getValue().getMessageProperties().getMessageId());
    assertEquals("{\"amount\":5}", new String(sent.getValue().getBody(), StandardCharsets.UTF_8));
  }

  @Test
  void brokerDownMakesTheForwarderFailSoTheOutboxRetries() {
    RabbitTemplate rabbit = mock(RabbitTemplate.class);
    doThrow(new AmqpConnectException(new RuntimeException("broker caído")))
      .when(rabbit)
      .send(any(String.class), any(String.class), any(Message.class));

    assertThrows(
      AmqpConnectException.class,
      () -> new RabbitForwarder(rabbit).on(new OutboxEvent("TransferCompleted", "{}"))
    );
  }

  @Test
  void consumerDelegatesToTheNotificationService() {
    NotificationService notifications = mock(NotificationService.class);
    UUID id = UUID.randomUUID();
    Message message = MessageBuilder.withBody("{\"amount\":5}".getBytes(StandardCharsets.UTF_8))
      .setMessageId(id.toString())
      .setReceivedRoutingKey("TransferCompleted")
      .build();

    new RabbitConsumer(notifications).on(message);

    verify(notifications).handle(id, "TransferCompleted", "{\"amount\":5}");
  }

  @Test
  void consumerErrorsPropagateSoSpringAmqpRetriesAndDeadLetters() {
    NotificationService notifications = mock(NotificationService.class);
    doThrow(new IllegalArgumentException("payload inválido"))
      .when(notifications)
      .handle(any(), any(), any());
    Message message = MessageBuilder.withBody("x".getBytes(StandardCharsets.UTF_8))
      .setMessageId(UUID.randomUUID().toString())
      .setReceivedRoutingKey("TransferCompleted")
      .build();

    assertThrows(IllegalArgumentException.class, () -> new RabbitConsumer(notifications).on(message));
  }
}

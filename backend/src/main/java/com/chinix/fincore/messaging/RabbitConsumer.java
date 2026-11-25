package com.chinix.fincore.messaging;

import com.chinix.fincore.notification.NotificationService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Consumidor asíncrono de notificaciones. Si lanza una excepción, Spring AMQP reintenta
 * (spring.rabbitmq.listener.simple.retry) y, agotados los intentos, el mensaje pasa a la DLQ.
 */
@Component
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true")
public class RabbitConsumer {

  private final NotificationService notifications;

  public RabbitConsumer(NotificationService notifications) {
    this.notifications = notifications;
  }

  @RabbitListener(queues = RabbitConfig.QUEUE)
  public void on(Message message) {
    notifications.handle(
      UUID.fromString(message.getMessageProperties().getMessageId()),
      message.getMessageProperties().getReceivedRoutingKey(),
      new String(message.getBody(), StandardCharsets.UTF_8)
    );
  }
}

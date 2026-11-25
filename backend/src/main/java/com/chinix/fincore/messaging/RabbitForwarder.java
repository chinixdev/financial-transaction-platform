package com.chinix.fincore.messaging;

import com.chinix.fincore.outbox.OutboxEvent;
import java.nio.charset.StandardCharsets;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Publica cada evento del outbox en RabbitMQ. Si el broker está caído la excepción sube al
 * OutboxPublisher, que reintenta: el evento no se pierde (esa es la gracia del outbox).
 */
@Component
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true")
public class RabbitForwarder {

  private final RabbitTemplate rabbit;

  public RabbitForwarder(RabbitTemplate rabbit) {
    this.rabbit = rabbit;
  }

  @EventListener
  public void on(OutboxEvent event) {
    Message message = MessageBuilder.withBody(event.payload.getBytes(StandardCharsets.UTF_8))
      .setMessageId(event.id.toString())
      .setContentType("application/json")
      .build();
    rabbit.send(RabbitConfig.EXCHANGE, event.type, message);
  }
}

package com.chinix.fincore.messaging;

import org.springframework.amqp.core.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología de RabbitMQ (solo con app.rabbitmq.enabled=true):
 *   exchange fincore.events --(#)--> cola fincore.notifications
 * Un mensaje que el consumidor rechaza tras sus reintentos va a la cola de errores
 * (Dead Letter Queue) fincore.notifications.dlq mediante el dead-letter-exchange.
 */
@Configuration
@ConditionalOnProperty(name = "app.rabbitmq.enabled", havingValue = "true")
public class RabbitConfig {

  public static final String EXCHANGE = "fincore.events";
  public static final String QUEUE = "fincore.notifications";
  public static final String DEAD_EXCHANGE = "fincore.events.dlx";
  public static final String DEAD_QUEUE = "fincore.notifications.dlq";

  @Bean
  TopicExchange eventsExchange() {
    return new TopicExchange(EXCHANGE);
  }

  @Bean
  DirectExchange deadLetterExchange() {
    return new DirectExchange(DEAD_EXCHANGE);
  }

  @Bean
  Queue notificationsQueue() {
    return QueueBuilder.durable(QUEUE)
      .withArgument("x-dead-letter-exchange", DEAD_EXCHANGE)
      .withArgument("x-dead-letter-routing-key", DEAD_QUEUE)
      .build();
  }

  @Bean
  Queue deadLetterQueue() {
    return QueueBuilder.durable(DEAD_QUEUE).build();
  }

  @Bean
  Binding notificationsBinding() {
    return BindingBuilder.bind(notificationsQueue()).to(eventsExchange()).with("#");
  }

  @Bean
  Binding deadLetterBinding() {
    return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(DEAD_QUEUE);
  }
}

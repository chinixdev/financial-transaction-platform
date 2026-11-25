package com.chinix.fincore.fraud;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Umbrales de las reglas. Se cambian en application.yml (app.fraud.*) sin tocar código. */
@ConfigurationProperties("app.fraud")
public record FraudProperties(
  /** Regla HIGH_AMOUNT: importe a partir del cual se considera atípico. */
  @DefaultValue("5000") BigDecimal maxAmount,
  /** Regla VELOCITY: transferencias permitidas en la ventana antes de sospechar. */
  @DefaultValue("5") int maxPerWindow,
  @DefaultValue("10") int windowMinutes,
  /** Regla DRAIN: fracción del saldo que, si se envía de golpe, es sospechosa. */
  @DefaultValue("0.90") BigDecimal drainRatio,
  /** Puntaje desde el cual la operación se bloquea. */
  @DefaultValue("70") int blockScore
) {}

package com.chinix.fincore.common;

import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class Money {

  private Money() {}

  /** Importe válido: positivo y con máximo 2 decimales. Siempre se normaliza a escala 2. */
  public static BigDecimal valid(BigDecimal amount) {
    if (amount == null || amount.scale() > 2 || amount.signum() <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid amount");
    }
    return amount.setScale(2);
  }
}

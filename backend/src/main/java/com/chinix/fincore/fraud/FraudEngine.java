package com.chinix.fincore.fraud;

import com.chinix.fincore.account.AccountRepository;
import com.chinix.fincore.transfer.TransferRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Motor de reglas: cada regla que se cumple suma puntos; el total clasifica el riesgo
 * (LOW &lt; 40, MEDIUM &lt; 70, HIGH) y, si llega a blockScore, la operación se bloquea.
 */
@Service
public class FraudEngine {

  // Peso de cada regla. HIGH_AMOUNT bloquea por sí sola con el umbral por defecto (70).
  static final int HIGH_AMOUNT = 70;
  static final int VELOCITY = 40;
  static final int DRAIN = 30;

  public record Decision(
    int score,
    String level,
    List<String> reasons,
    boolean blocked
  ) {}

  private final FraudProperties props;
  private final FraudAlertRepository alerts;
  private final AccountRepository accounts;
  private final TransferRepository transfers;

  public FraudEngine(
    FraudProperties props,
    FraudAlertRepository alerts,
    AccountRepository accounts,
    TransferRepository transfers
  ) {
    this.props = props;
    this.alerts = alerts;
    this.accounts = accounts;
    this.transfers = transfers;
  }

  /** Lógica pura (sin base de datos): fácil de probar y de explicar. */
  public Decision evaluate(BigDecimal amount, BigDecimal balance, long recentTransfers) {
    int score = 0;
    List<String> reasons = new ArrayList<>();
    if (amount.compareTo(props.maxAmount()) >= 0) {
      score += HIGH_AMOUNT;
      reasons.add("HIGH_AMOUNT");
    }
    if (recentTransfers >= props.maxPerWindow()) {
      score += VELOCITY;
      reasons.add("VELOCITY");
    }
    if (
      balance.signum() > 0 &&
      amount.compareTo(balance.multiply(props.drainRatio())) >= 0
    ) {
      score += DRAIN;
      reasons.add("DRAIN_BALANCE");
    }
    String level = score >= 70 ? "HIGH" : score >= 40 ? "MEDIUM" : "LOW";
    return new Decision(score, level, reasons, score >= props.blockScore());
  }

  /**
   * Se ejecuta ANTES de la transacción de la transferencia y en su propia transacción:
   * así la alerta queda guardada aunque la operación se rechace (noRollbackFor).
   */
  @Transactional(noRollbackFor = ResponseStatusException.class)
  public void check(UUID user, UUID sourceAccountId, BigDecimal amount) {
    BigDecimal balance = accounts
      .findBalanceById(sourceAccountId)
      .orElse(BigDecimal.ZERO);
    long recent = transfers.countBySenderIdAndTypeAndCreatedAtAfter(
      sourceAccountId,
      "TRANSFER",
      Instant.now().minus(props.windowMinutes(), ChronoUnit.MINUTES)
    );
    Decision d = evaluate(amount, balance, recent);
    if (!d.level().equals("LOW")) {
      alerts.save(new FraudAlert(user, sourceAccountId, amount, d));
    }
    if (d.blocked()) {
      throw new ResponseStatusException(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Operation blocked by fraud rules: " + String.join(",", d.reasons())
      );
    }
  }
}

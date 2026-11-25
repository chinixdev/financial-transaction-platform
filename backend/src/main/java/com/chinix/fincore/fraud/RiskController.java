package com.chinix.fincore.fraud;

import com.chinix.fincore.auth.AuthService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/risk")
public class RiskController {

  public record AlertView(
    UUID id,
    BigDecimal amount,
    int score,
    String level,
    String decision,
    String reasons,
    Instant createdAt
  ) {}

  private final FraudAlertRepository alerts;
  private final FraudProperties props;
  private final AuthService auth;

  public RiskController(
    FraudAlertRepository alerts,
    FraudProperties props,
    AuthService auth
  ) {
    this.alerts = alerts;
    this.props = props;
    this.auth = auth;
  }

  @GetMapping("/alerts")
  public List<AlertView> alerts() {
    return alerts
      .findTop50ByUserIdOrderByCreatedAtDesc(auth.currentUserId())
      .stream()
      .map(a ->
        new AlertView(a.id, a.amount, a.score, a.level, a.decision, a.reasons, a.createdAt)
      )
      .toList();
  }

  /** Reglas y umbrales vigentes (solo lectura). */
  @GetMapping("/rules")
  public FraudProperties rules() {
    return props;
  }
}

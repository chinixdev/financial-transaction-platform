package com.chinix.fincore.system;

import com.chinix.fincore.outbox.*;
import com.chinix.fincore.payment.PaymentRepository;
import com.chinix.fincore.transfer.LedgerRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

/** Estado operativo del sistema. Solo administradores (ver SecurityConfig). */
@RestController
@RequestMapping("/api/system")
public class SystemController {

  public record Health(
    String status,
    String database,
    long outboxPending,
    long outboxFailed,
    String paymentCircuit,
    long pendingPayments,
    boolean ledgerBalanced
  ) {}

  private final JdbcTemplate jdbc;
  private final OutboxRepository outbox;
  private final OutboxPublisher publisher;
  private final PaymentRepository payments;
  private final LedgerRepository ledger;
  private final CircuitBreaker breaker;

  public SystemController(
    JdbcTemplate jdbc,
    OutboxRepository outbox,
    OutboxPublisher publisher,
    PaymentRepository payments,
    LedgerRepository ledger,
    CircuitBreaker breaker
  ) {
    this.jdbc = jdbc;
    this.outbox = outbox;
    this.publisher = publisher;
    this.payments = payments;
    this.ledger = ledger;
    this.breaker = breaker;
  }

  @GetMapping("/health")
  public Health health() {
    String db;
    try {
      jdbc.queryForObject("select 1", Integer.class);
      db = "UP";
    } catch (RuntimeException e) {
      db = "DOWN";
    }
    long failed = outbox.countByStatus("FAILED");
    boolean balanced = ledger.total("DEBIT").compareTo(ledger.total("CREDIT")) == 0;
    String circuit = breaker.getState().name();
    boolean healthy =
      db.equals("UP") && failed == 0 && balanced && !circuit.equals("OPEN");
    return new Health(
      healthy ? "UP" : "DEGRADED",
      db,
      outbox.countByStatus("PENDING"),
      failed,
      circuit,
      payments.countByStatus("PENDING"),
      balanced
    );
  }

  /** Cola de errores: reintenta los eventos que agotaron sus intentos. */
  @PostMapping("/outbox/requeue")
  public Map<String, Integer> requeue() {
    return Map.of("requeued", publisher.requeueFailed());
  }
}

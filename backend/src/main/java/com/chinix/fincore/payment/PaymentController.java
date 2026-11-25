package com.chinix.fincore.payment;

import com.chinix.fincore.auth.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

  public record CreateRequest(
    @NotNull UUID accountId,
    @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount
  ) {}

  public record PaymentView(
    UUID id,
    UUID accountId,
    BigDecimal amount,
    String provider,
    String providerRef,
    String status,
    Instant createdAt
  ) {}

  private final PaymentService service;
  private final AuthService auth;

  public PaymentController(PaymentService service, AuthService auth) {
    this.service = service;
    this.auth = auth;
  }

  @PostMapping
  public PaymentView create(
    @Valid @RequestBody CreateRequest r,
    @RequestHeader("Idempotency-Key") String key
  ) {
    return view(
      service.create(auth.currentUserId(), r.accountId(), r.amount(), key)
    );
  }

  @GetMapping
  public List<PaymentView> mine() {
    return service
      .mine(auth.currentUserId())
      .stream()
      .map(PaymentController::view)
      .toList();
  }

  private static PaymentView view(Payment p) {
    return new PaymentView(
      p.id,
      p.accountId,
      p.amount,
      p.provider,
      p.providerRef,
      p.status,
      p.createdAt
    );
  }
}

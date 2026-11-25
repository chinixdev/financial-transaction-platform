package com.chinix.fincore.transfer;

import com.chinix.fincore.auth.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

  private final TransferService service;
  private final AuthService auth;

  public TransferController(TransferService service, AuthService auth) {
    this.service = service;
    this.auth = auth;
  }

  public record SendRequest(
    @NotNull UUID sourceAccountId,
    @NotBlank String destinationAccountNumber,
    @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
    @Size(max = 200) String reference
  ) {}

  /** amount vacío = reembolso total de lo que falte. */
  public record RefundRequest(
    @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount
  ) {}

  public record TransferView(
    UUID id,
    UUID senderId,
    UUID recipientId,
    BigDecimal amount,
    String status,
    String type,
    String reference,
    Instant createdAt
  ) {}

  @PostMapping
  public TransferService.Receipt send(
    @Valid @RequestBody SendRequest r,
    @RequestHeader("Idempotency-Key") String key
  ) {
    return service.send(
      auth.currentUserId(),
      new TransferService.Request(
        r.sourceAccountId(),
        r.destinationAccountNumber(),
        r.amount(),
        r.reference()
      ),
      key
    );
  }

  @PostMapping("/{id}/refunds")
  public TransferService.Receipt refund(
    @PathVariable UUID id,
    @Valid @RequestBody(required = false) RefundRequest r,
    @RequestHeader("Idempotency-Key") String key
  ) {
    return service.refund(
      auth.currentUserId(),
      id,
      r == null ? null : r.amount(),
      key
    );
  }

  @GetMapping
  public List<TransferView> mine() {
    return service
      .mine(auth.currentUserId())
      .stream()
      .map(t ->
        new TransferView(
          t.id,
          t.senderId,
          t.recipientId,
          t.amount,
          t.status,
          t.type,
          t.reference,
          t.createdAt
        )
      )
      .toList();
  }
}

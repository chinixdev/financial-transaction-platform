package com.chinix.fincore.transfer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

  private final TransferService service;

  public TransferController(TransferService service) {
    this.service = service;
  }

  public record SendRequest(
    @NotNull UUID sourceAccountId,
    @NotBlank String destinationAccountNumber,
    @NotNull
    @DecimalMin("0.01")
    @Digits(integer = 17, fraction = 2)
    BigDecimal amount,
    String reference
  ) {}

  @PostMapping
  public TransferService.Receipt send(
    @Valid @RequestBody SendRequest r,
    @RequestHeader("Idempotency-Key") String key
  ) {
    return service.send(
      new TransferService.Request(
        r.sourceAccountId(),
        r.destinationAccountNumber(),
        r.amount(),
        r.reference()
      ),
      key
    );
  }

  public record TransferView(
    UUID id,
    UUID senderId,
    UUID recipientId,
    BigDecimal amount,
    String status,
    String reference,
    java.time.Instant createdAt
  ) {}

  @GetMapping
  public List<TransferView> mine() {
    return service
      .mine()
      .stream()
      .map(t ->
        new TransferView(
          t.id,
          t.senderId,
          t.recipientId,
          t.amount,
          t.status,
          t.reference,
          t.createdAt
        )
      )
      .toList();
  }
}

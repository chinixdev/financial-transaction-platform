package com.chinix.fincore.reconciliation;

import java.time.Instant;
import java.util.*;
import org.springframework.web.bind.annotation.*;

/** Solo administradores (ver SecurityConfig). */
@RestController
@RequestMapping("/api/reconciliation")
public class ReconciliationController {

  public record FindingView(
    UUID id,
    String type,
    String providerRef,
    String detail,
    String status,
    Instant createdAt
  ) {}

  private final ReconciliationService service;

  public ReconciliationController(ReconciliationService service) {
    this.service = service;
  }

  @PostMapping("/run")
  public List<FindingView> run() {
    service.run(); // en su propia transacción: así las diferencias nuevas ya traen su fecha
    return findings();
  }

  @GetMapping("/findings")
  public List<FindingView> findings() {
    return service.open().stream().map(ReconciliationController::view).toList();
  }

  @PostMapping("/findings/{id}/resolve")
  public FindingView resolve(@PathVariable UUID id) {
    return view(service.resolve(id));
  }

  private static FindingView view(Finding f) {
    return new FindingView(f.id, f.type, f.providerRef, f.detail, f.status, f.createdAt);
  }
}

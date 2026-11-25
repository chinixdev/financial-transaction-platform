package com.chinix.fincore.reconciliation;

import com.chinix.fincore.payment.*;
import com.chinix.fincore.payment.PaymentGateway.ProviderCharge;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Conciliación: compara los pagos de nuestra base con los que el proveedor tiene registrados
 * y deja una "diferencia" por cada discrepancia. Una sola es corregible automáticamente:
 * el proveedor cobró y nosotros seguimos en PENDING (webhook perdido).
 */
@Service
public class ReconciliationService {

  private final PaymentGateway gateway;
  private final PaymentRepository payments;
  private final PaymentService paymentService;
  private final FindingRepository findings;

  public ReconciliationService(
    PaymentGateway gateway,
    PaymentRepository payments,
    PaymentService paymentService,
    FindingRepository findings
  ) {
    this.gateway = gateway;
    this.payments = payments;
    this.paymentService = paymentService;
    this.findings = findings;
  }

  /** Ejecuta la conciliación y devuelve las diferencias abiertas (sin duplicar las ya conocidas). */
  @Transactional
  public List<Finding> run() {
    Map<String, ProviderCharge> remote = gateway
      .listCharges()
      .stream()
      .collect(Collectors.toMap(ProviderCharge::providerRef, c -> c, (a, b) -> a));
    List<Payment> local = payments
      .findByProvider(gateway.name())
      .stream()
      .filter(p -> p.providerRef != null)
      .toList();

    Set<String> localRefs = new HashSet<>();
    for (Payment p : local) {
      localRefs.add(p.providerRef);
      ProviderCharge r = remote.get(p.providerRef);
      if (r == null) {
        if (p.status.equals("SUCCEEDED")) {
          report(Finding.MISSING_AT_PROVIDER, p, "Acreditado aquí, inexistente en el proveedor");
        }
      } else if (r.amount().compareTo(p.amount) != 0) {
        report(Finding.AMOUNT_MISMATCH, p, "Nosotros " + p.amount + " / proveedor " + r.amount());
      } else if (r.status().equals("succeeded") && !p.status.equals("SUCCEEDED")) {
        report(Finding.STATUS_MISMATCH, p, "Proveedor: succeeded / nosotros: " + p.status);
      } else if (!r.status().equals("succeeded") && p.status.equals("SUCCEEDED")) {
        report(Finding.STATUS_MISMATCH, p, "Proveedor: " + r.status() + " / nosotros: SUCCEEDED");
      }
    }
    for (ProviderCharge r : remote.values()) {
      if (r.status().equals("succeeded") && !localRefs.contains(r.providerRef())) {
        report(Finding.MISSING_INTERNALLY, null, "Cobro del proveedor por " + r.amount() + " sin pago interno", r.providerRef());
      }
    }
    return open();
  }

  @Transactional(readOnly = true)
  public List<Finding> open() {
    return findings.findTop100ByStatusOrderByCreatedAtDesc("OPEN");
  }

  /**
   * Corrige una diferencia STATUS_MISMATCH donde el proveedor cobró y nosotros seguimos PENDING:
   * acredita el pago (una sola vez, aunque se pulse dos veces). El resto requiere revisión humana.
   */
  @Transactional
  public Finding resolve(UUID id) {
    Finding f = findings
      .findById(id)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Finding not found"));
    if (f.status.equals("RESOLVED")) return f;
    Payment p = f.paymentId == null ? null : payments.findById(f.paymentId).orElse(null);
    boolean recoverable =
      f.type.equals(Finding.STATUS_MISMATCH) && p != null && p.status.equals("PENDING");
    if (!recoverable) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requires manual review");
    }
    paymentService.complete(p.providerRef);
    f.status = "RESOLVED";
    f.resolvedAt = Instant.now();
    return f;
  }

  private void report(String type, Payment p, String detail) {
    report(type, p, detail, p.providerRef);
  }

  private void report(String type, Payment p, String detail, String ref) {
    if (findings.existsByTypeAndProviderRefAndStatus(type, ref, "OPEN")) return;
    findings.save(new Finding(type, ref, p == null ? null : p.id, detail));
  }
}

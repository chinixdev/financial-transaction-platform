package com.chinix.fincore.reconciliation;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Una diferencia entre nuestros registros y los del proveedor de pagos. */
@Entity
@Table(name = "reconciliation_findings")
public class Finding {

  /** Proveedor dice "pagado" y nosotros no (¿webhook perdido?). Se puede resolver. */
  public static final String STATUS_MISMATCH = "STATUS_MISMATCH";
  /** El proveedor cobró algo que no existe en nuestro sistema. Revisión manual. */
  public static final String MISSING_INTERNALLY = "MISSING_INTERNALLY";
  /** Acreditamos un pago que el proveedor no tiene. Revisión manual. */
  public static final String MISSING_AT_PROVIDER = "MISSING_AT_PROVIDER";
  /** Mismo pago, distinto importe. Revisión manual. */
  public static final String AMOUNT_MISMATCH = "AMOUNT_MISMATCH";

  @Id
  public UUID id;

  public String type;

  @Column(name = "provider_ref")
  public String providerRef;

  @Column(name = "payment_id")
  public UUID paymentId;

  public String detail;

  /** OPEN o RESOLVED. */
  public String status = "OPEN";

  @Column(name = "created_at", insertable = false, updatable = false)
  public Instant createdAt;

  @Column(name = "resolved_at")
  public Instant resolvedAt;

  protected Finding() {}

  public Finding(String type, String providerRef, UUID paymentId, String detail) {
    this.id = UUID.randomUUID();
    this.type = type;
    this.providerRef = providerRef;
    this.paymentId = paymentId;
    this.detail = detail;
  }
}

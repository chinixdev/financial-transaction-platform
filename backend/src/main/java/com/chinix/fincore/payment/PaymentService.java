package com.chinix.fincore.payment;

import com.chinix.fincore.account.*;
import com.chinix.fincore.common.Money;
import com.chinix.fincore.transfer.TransferService;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Recarga de saldo vía pasarela. Pasos: (1) guardar el pago PENDING, (2) llamar al proveedor
 * FUERA de la transacción (una llamada de red no debe retener bloqueos), (3) acreditar cuando
 * el pago se confirma (respuesta síncrona o webhook). complete() es seguro de repetir.
 */
@Service
public class PaymentService {

  private final PaymentRepository payments;
  private final AccountRepository accounts;
  private final TransferService transfers;
  private final PaymentGateway gateway;
  private final TransactionTemplate tx;

  public PaymentService(
    PaymentRepository payments,
    AccountRepository accounts,
    TransferService transfers,
    PaymentGateway gateway,
    TransactionTemplate tx
  ) {
    this.payments = payments;
    this.accounts = accounts;
    this.transfers = transfers;
    this.gateway = gateway;
    this.tx = tx;
  }

  public Payment create(UUID user, UUID accountId, BigDecimal rawAmount, String key) {
    if (key == null || key.isBlank() || key.length() > 100) {
      throw bad("Idempotency-Key required (max 100)");
    }
    BigDecimal amount = Money.valid(rawAmount);

    // 1) Guardar PENDING (o devolver el pago existente si es un reintento).
    Payment p = tx.execute(s -> {
      Optional<Payment> existing = payments.findByUserIdAndIdempotencyKey(user, key);
      if (existing.isPresent()) {
        Payment e = existing.get();
        if (!e.accountId.equals(accountId) || e.amount.compareTo(amount) != 0) {
          throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Idempotency key reused with different payload"
          );
        }
        return e;
      }
      Account account = accounts
        .findById(accountId)
        .orElseThrow(() -> bad("Account not found"));
      if (!account.ownerId.equals(user)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your account");
      }
      return payments.saveAndFlush(
        new Payment(user, accountId, amount, gateway.name(), key)
      );
    });
    if (p.providerRef != null) return p; // reintento: ya se envió al proveedor

    // 2) Llamar al proveedor (fuera de transacción).
    PaymentGateway.Charge charge;
    try {
      charge = gateway.createCharge(amount, "PEN", p.id.toString());
    } catch (RuntimeException e) {
      tx.executeWithoutResult(s ->
        payments.findById(p.id).orElseThrow().status = "FAILED"
      );
      throw new ResponseStatusException(
        HttpStatus.BAD_GATEWAY,
        "Payment provider unavailable"
      );
    }
    tx.executeWithoutResult(s ->
      payments.findById(p.id).orElseThrow().providerRef = charge.providerRef()
    );

    // 3) Si el proveedor ya confirmó, acreditar ahora; si no, esperará al webhook.
    if (charge.status().equals("succeeded")) complete(charge.providerRef());
    return payments.findById(p.id).orElseThrow();
  }

  /** Acredita el pago una sola vez. Devuelve su id, o null si el pago no existe. */
  public UUID complete(String providerRef) {
    return tx.execute(s ->
      payments
        .lockByProviderRef(providerRef)
        .map(p -> {
          if (p.status.equals("PENDING")) {
            transfers.topUp(p.accountId, p.amount, "Top-up " + p.id);
            p.status = "SUCCEEDED";
          }
          return p.id;
        })
        .orElse(null)
    );
  }

  public UUID fail(String providerRef) {
    return tx.execute(s ->
      payments
        .lockByProviderRef(providerRef)
        .map(p -> {
          if (p.status.equals("PENDING")) p.status = "FAILED";
          return p.id;
        })
        .orElse(null)
    );
  }

  public List<Payment> mine(UUID user) {
    return payments.findTop30ByUserIdOrderByCreatedAtDesc(user);
  }

  private ResponseStatusException bad(String m) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
  }
}

package com.chinix.fincore.payment;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
  Optional<Payment> findByUserIdAndIdempotencyKey(UUID userId, String key);

  List<Payment> findTop30ByUserIdOrderByCreatedAtDesc(UUID userId);

  List<Payment> findByProvider(String provider);

  long countByStatus(String status);

  // Bloquea la fila: el webhook y la respuesta síncrona pueden llegar a la vez
  // y solo uno debe acreditar el saldo.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Payment p where p.providerRef = :ref")
  Optional<Payment> lockByProviderRef(@Param("ref") String ref);
}

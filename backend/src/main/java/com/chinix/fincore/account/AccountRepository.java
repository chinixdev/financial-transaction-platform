package com.chinix.fincore.account;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {
  List<Account> findByOwnerId(UUID ownerId);
  Optional<Account> findByAccountNumber(String number);

  // Solo el id: si se cargara la entidad completa antes de bloquear, Hibernate la dejaría en
  // caché con un saldo viejo y lockById() devolvería ese saldo desactualizado (pérdida de dinero).
  @Query("select a.id from Account a where a.accountNumber = :number")
  Optional<UUID> findIdByAccountNumber(@Param("number") String number);

  // Lectura sin cargar la entidad (para el antifraude, que no debe tocar la caché de cuentas).
  @Query("select a.balance from Account a where a.id = :id")
  Optional<java.math.BigDecimal> findBalanceById(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Account a where a.id = :id")
  Optional<Account> lockById(@Param("id") UUID id);
}

package com.chinix.fincore.account;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {
  List<Account> findByOwnerId(UUID ownerId);
  Optional<Account> findByAccountNumber(String number);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Account a where a.id = :id")
  Optional<Account> lockById(@Param("id") UUID id);
}

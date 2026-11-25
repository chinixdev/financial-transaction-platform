package com.chinix.fincore.transfer;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface LedgerRepository extends JpaRepository<LedgerEntry, UUID> {
  List<LedgerEntry> findTop50ByAccountIdInOrderByCreatedAtDesc(
    Collection<UUID> accountIds
  );

  @Query(
    "select coalesce(sum(l.amount), 0) from LedgerEntry l where l.direction = :direction"
  )
  BigDecimal total(@Param("direction") String direction);
}

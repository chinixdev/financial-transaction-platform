package com.chinix.fincore.transfer;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {
  List<Transfer> findTop30BySenderIdInOrRecipientIdInOrderByCreatedAtDesc(
    Collection<UUID> senders,
    Collection<UUID> recipients
  );

  long countBySenderIdAndTypeAndCreatedAtAfter(
    UUID senderId,
    String type,
    java.time.Instant since
  );

  @Query(
    "select coalesce(sum(t.amount), 0) from Transfer t where t.originalTransferId = :id"
  )
  BigDecimal totalRefunded(@Param("id") UUID originalId);
}

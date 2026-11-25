package com.chinix.fincore.transfer;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {
  List<Transfer> findTop30BySenderIdInOrRecipientIdInOrderByCreatedAtDesc(
    Collection<UUID> senders,
    Collection<UUID> recipients
  );
}

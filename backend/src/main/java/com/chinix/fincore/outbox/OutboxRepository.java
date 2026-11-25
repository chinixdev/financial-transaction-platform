package com.chinix.fincore.outbox;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
  List<OutboxEvent> findTop50ByStatusOrderByCreatedAt(String status);

  List<OutboxEvent> findByStatus(String status);

  long countByStatus(String status);
}

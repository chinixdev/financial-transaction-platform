package com.chinix.fincore.webhook;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {
  boolean existsByEventId(String eventId);

  List<WebhookEvent> findTop50ByPaymentIdInOrderByReceivedAtDesc(Collection<UUID> paymentIds);
}

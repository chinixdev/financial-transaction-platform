package com.chinix.fincore.notification;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
  List<Notification> findTop50ByUserIdOrderByCreatedAtDesc(UUID userId);

  boolean existsByEventIdAndUserId(UUID eventId, UUID userId);
}

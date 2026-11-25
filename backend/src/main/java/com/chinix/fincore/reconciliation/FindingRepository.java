package com.chinix.fincore.reconciliation;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FindingRepository extends JpaRepository<Finding, UUID> {
  List<Finding> findTop100ByStatusOrderByCreatedAtDesc(String status);

  boolean existsByTypeAndProviderRefAndStatus(String type, String providerRef, String status);
}

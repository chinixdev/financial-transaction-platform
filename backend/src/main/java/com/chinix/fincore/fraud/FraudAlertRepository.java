package com.chinix.fincore.fraud;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudAlertRepository extends JpaRepository<FraudAlert, UUID> {
  List<FraudAlert> findTop50ByUserIdOrderByCreatedAtDesc(UUID userId);
}

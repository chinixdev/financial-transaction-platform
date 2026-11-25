package com.chinix.fincore.transfer;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRepository
  extends JpaRepository<IdempotencyKey, UUID>
{
  Optional<IdempotencyKey> findByUserIdAndRequestKey(
    UUID userId,
    String requestKey
  );
}

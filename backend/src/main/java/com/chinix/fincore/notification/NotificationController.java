package com.chinix.fincore.notification;

import com.chinix.fincore.auth.AuthService;
import java.time.Instant;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

  public record NotificationView(UUID id, String type, String message, Instant createdAt) {}

  private final NotificationRepository repo;
  private final AuthService auth;

  public NotificationController(NotificationRepository repo, AuthService auth) {
    this.repo = repo;
    this.auth = auth;
  }

  @GetMapping
  public List<NotificationView> mine() {
    return repo
      .findTop50ByUserIdOrderByCreatedAtDesc(auth.currentUserId())
      .stream()
      .map(n -> new NotificationView(n.id, n.type, n.message, n.createdAt))
      .toList();
  }
}

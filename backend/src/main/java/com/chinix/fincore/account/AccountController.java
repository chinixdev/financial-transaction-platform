package com.chinix.fincore.account;

import com.chinix.fincore.auth.AuthService;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

  private final AccountRepository repo;
  private final AuthService auth;

  public AccountController(AccountRepository repo, AuthService auth) {
    this.repo = repo;
    this.auth = auth;
  }

  public record AccountView(
    UUID id,
    String accountNumber,
    String currency,
    java.math.BigDecimal balance,
    String status
  ) {}

  @GetMapping
  public List<AccountView> mine() {
    return repo
      .findByOwnerId(auth.currentUserId())
      .stream()
      .map(a ->
        new AccountView(a.id, a.accountNumber, a.currency, a.balance, a.status)
      )
      .toList();
  }
}

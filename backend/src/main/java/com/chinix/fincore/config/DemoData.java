package com.chinix.fincore.config;

import com.chinix.fincore.account.AccountRepository;
import com.chinix.fincore.auth.*;
import java.math.BigDecimal;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Solo perfil "local": crea dos usuarios con saldo para poder probar sin configurar nada.
 * Es un atajo de desarrollo: el saldo inicial no pasa por el ledger.
 */
@Component
@Profile("local")
public class DemoData implements CommandLineRunner {

  public static final String PASSWORD = "DemoFinCore2026";

  private final AuthService auth;
  private final UserRepository users;
  private final AccountRepository accounts;

  public DemoData(
    AuthService auth,
    UserRepository users,
    AccountRepository accounts
  ) {
    this.auth = auth;
    this.users = users;
    this.accounts = accounts;
  }

  @Override
  @Transactional
  public void run(String... args) {
    create("demo@fincore.dev", "Ángel Demo", "1000.00", "ADMIN");
    create("cliente@fincore.dev", "Cliente Demo", "250.00", "USER");
  }

  private void create(String email, String name, String balance, String role) {
    if (users.findByEmail(email).isPresent()) return;
    auth.register(email, PASSWORD, name);
    users
      .findByEmail(email)
      .ifPresent(u -> {
        u.role = role;
        accounts
          .findByOwnerId(u.id)
          .forEach(a -> a.balance = new BigDecimal(balance));
      });
  }
}

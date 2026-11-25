package com.chinix.fincore;

import com.chinix.fincore.account.*;
import com.chinix.fincore.auth.*;
import com.chinix.fincore.transfer.*;
import com.chinix.fincore.transfer.TransferService.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Ayudantes compartidos por las pruebas de integración (cada subclase lleva @SpringBootTest). */
abstract class TestBase {

  @Autowired TransferService service;
  @Autowired AuthService auth;
  @Autowired UserRepository users;
  @Autowired AccountRepository accounts;
  @Autowired LedgerRepository ledger;

  /** Usuario de prueba con una cuenta y el saldo indicado. */
  record Wallet(UUID user, UUID accountId, String number) {}

  Wallet wallet(String balance) {
    String email = UUID.randomUUID() + "@test.dev";
    auth.register(email, "password-12345", "Test");
    UUID user = users.findByEmail(email).orElseThrow().id;
    Account a = accounts.findByOwnerId(user).get(0);
    a.balance = new BigDecimal(balance);
    accounts.save(a);
    return new Wallet(user, a.id, a.accountNumber);
  }

  BigDecimal balance(Wallet w) {
    return accounts.findById(w.accountId()).orElseThrow().balance;
  }

  Receipt send(Wallet from, Wallet to, String amount, String key) {
    return service.send(
      from.user(),
      new Request(from.accountId(), to.number(), new BigDecimal(amount), "test"),
      key
    );
  }

  String key() {
    return "k-" + UUID.randomUUID();
  }

  /** Ejecuta la acción esperando un rechazo de negocio; devuelve su código HTTP. */
  HttpStatus statusOf(Runnable action) {
    ResponseStatusException e = assertThrows(ResponseStatusException.class, action::run);
    return HttpStatus.valueOf(e.getStatusCode().value());
  }

  boolean ledgerIsBalanced() {
    return ledger.total("DEBIT").compareTo(ledger.total("CREDIT")) == 0;
  }

  /** Lanza n tareas a la vez; devuelve cuántas terminaron sin excepción de negocio. */
  int runConcurrently(int n, java.util.function.IntFunction<Object> task) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(n);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger ok = new AtomicInteger();
    List<Future<?>> futures = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      int index = i;
      futures.add(
        pool.submit(() -> {
          start.await();
          try {
            task.apply(index);
            ok.incrementAndGet();
          } catch (ResponseStatusException | org.springframework.dao.DataAccessException expected) {
            // rechazo de negocio o conflicto: es un resultado válido
          }
          return null;
        })
      );
    }
    start.countDown();
    for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
    pool.shutdown();
    return ok.get();
  }
}

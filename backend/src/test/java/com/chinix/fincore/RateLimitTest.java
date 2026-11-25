package com.chinix.fincore;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chinix.fincore.ratelimit.InMemoryRateLimiter;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = { "app.outbox.delay-ms=3600000", "app.rate-limit.auth-per-minute=3" })
@AutoConfigureMockMvc
class RateLimitTest {

  @Autowired MockMvc mvc;

  @Test
  void loginIsBlockedWith429AfterTooManyAttempts() throws Exception {
    String wrongPassword = "{\"email\":\"demo@fincore.dev\",\"password\":\"incorrecta-12345\"}";
    for (int i = 0; i < 3; i++) {
      mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrongPassword))
        .andExpect(status().isUnauthorized());
    }
    // 4.º intento: fuerza bruta frenada antes de llegar a comprobar la contraseña.
    mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrongPassword))
      .andExpect(status().isTooManyRequests());
  }

  @Test
  void limiterAllowsUpToTheLimitPerKeyThenRecoversWhenTheWindowPasses() throws Exception {
    InMemoryRateLimiter limiter = new InMemoryRateLimiter();
    Duration window = Duration.ofMillis(80);

    assertTrue(limiter.allow("a", 2, window));
    assertTrue(limiter.allow("a", 2, window));
    assertFalse(limiter.allow("a", 2, window));
    assertTrue(limiter.allow("b", 2, window)); // otra clave no se ve afectada

    Thread.sleep(120);
    assertTrue(limiter.allow("a", 2, window)); // nueva ventana
  }
}

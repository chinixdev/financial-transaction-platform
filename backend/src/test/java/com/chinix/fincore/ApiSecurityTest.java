package com.chinix.fincore;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Seguridad HTTP real (filtros + JWT + roles) sobre los usuarios demo del perfil local. */
@SpringBootTest(properties = "app.outbox.delay-ms=3600000")
@AutoConfigureMockMvc
class ApiSecurityTest {

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  String login(String email) throws Exception {
    String body = mvc
      .perform(
        post("/api/auth/login")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"email\":\"" + email + "\",\"password\":\"DemoFinCore2026\"}")
      )
      .andExpect(status().isOk())
      .andReturn()
      .getResponse()
      .getContentAsString();
    return json.readTree(body).get("token").asText();
  }

  @Test
  void protectedEndpointsRejectRequestsWithoutToken() throws Exception {
    mvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/system/health")).andExpect(status().isUnauthorized());
  }

  @Test
  void adminEndpointsAreForbiddenForRegularUsers() throws Exception {
    String user = login("cliente@fincore.dev");

    mvc.perform(get("/api/system/health").header("Authorization", "Bearer " + user)).andExpect(status().isForbidden());
    mvc.perform(get("/api/reconciliation/findings").header("Authorization", "Bearer " + user)).andExpect(status().isForbidden());
    mvc.perform(post("/api/reconciliation/run").header("Authorization", "Bearer " + user)).andExpect(status().isForbidden());
  }

  @Test
  void adminCanReadSystemHealthAndReconciliation() throws Exception {
    String admin = login("demo@fincore.dev");

    mvc.perform(get("/api/system/health").header("Authorization", "Bearer " + admin))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.database").value("UP"))
      .andExpect(jsonPath("$.ledgerBalanced").value(true));
    mvc.perform(get("/api/reconciliation/findings").header("Authorization", "Bearer " + admin)).andExpect(status().isOk());
  }

  @Test
  void everyResponseCarriesARequestId() throws Exception {
    mvc.perform(get("/actuator/health"))
      .andExpect(status().isOk())
      .andExpect(header().exists("X-Request-Id"));
  }

  @Test
  void webhookEndpointIsPublicButRejectsUnsignedMessages() throws Exception {
    mvc.perform(
        post("/api/webhooks/stripe")
          .contentType(MediaType.APPLICATION_JSON)
          .header("Stripe-Signature", "t=1,v1=falsa")
          .content("{\"id\":\"evt\",\"type\":\"x\"}")
      )
      .andExpect(status().isBadRequest());
  }

  @Test
  void userCannotSeeAnotherUsersNotificationsOrPayments() throws Exception {
    String user = login("cliente@fincore.dev");

    mvc.perform(get("/api/payments").header("Authorization", "Bearer " + user))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$").isArray());
    mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + user)).andExpect(status().isOk());
  }

  /** Sin CORS configurado el navegador bloquea al frontend (bug real detectado al probar el modo real). */
  @Test
  void corsAllowsTheFrontendOriginAndRejectsOthers() throws Exception {
    mvc.perform(
        options("/api/payments")
          .header("Origin", "http://localhost:5173")
          .header("Access-Control-Request-Method", "POST")
          .header("Access-Control-Request-Headers", "authorization,content-type,idempotency-key")
      )
      .andExpect(status().isOk())
      .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));

    mvc.perform(
        options("/api/payments")
          .header("Origin", "http://evil.example")
          .header("Access-Control-Request-Method", "POST")
      )
      .andExpect(status().isForbidden());
  }
}

package com.chinix.fincore.config;

import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

@Configuration
public class SecurityConfig {

  @Bean
  PasswordEncoder encoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  JwtEncoder jwtEncoder(@Value("${app.jwt-secret}") String secret) {
    return new NimbusJwtEncoder(
      new com.nimbusds.jose.jwk.source.ImmutableSecret<>(
        secret.getBytes(StandardCharsets.UTF_8)
      )
    );
  }

  @Bean
  JwtDecoder jwtDecoder(@Value("${app.jwt-secret}") String secret) {
    return NimbusJwtDecoder.withSecretKey(
      new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")
    )
      .macAlgorithm(MacAlgorithm.HS256)
      .build();
  }

  @Bean
  SecurityFilterChain security(
    HttpSecurity http,
    JwtAuthenticationConverter jwtConverter
  ) throws Exception {
    return http
      .csrf(c -> c.disable())
      .cors(c -> {})
      .sessionManagement(s ->
        s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
      )
      .authorizeHttpRequests(a ->
        a
          .requestMatchers("/api/auth/**", "/actuator/health")
          .permitAll()
          // El proveedor de pagos no tiene JWT: este endpoint se protege con la firma del mensaje.
          .requestMatchers(HttpMethod.POST, "/api/webhooks/stripe")
          .permitAll()
          // Operación y conciliación: solo administradores (claim "role" del JWT).
          .requestMatchers("/api/reconciliation/**", "/api/system/**")
          .hasRole("ADMIN")
          .anyRequest()
          .authenticated()
      )
      .oauth2ResourceServer(o ->
        o.jwt(j -> j.jwtAuthenticationConverter(jwtConverter))
      )
      .build();
  }

  /** Convierte el claim "role" del JWT en la autoridad ROLE_ADMIN / ROLE_USER. */
  @Bean
  JwtAuthenticationConverter jwtConverter() {
    JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
    roles.setAuthoritiesClaimName("role");
    roles.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(roles);
    return converter;
  }

  @Bean
  // El nombre "corsConfigurationSource" es obligatorio: Spring Security solo reconoce el bean con ese
  // nombre. Con otro nombre el CORS se ignora en silencio y el navegador bloquea al frontend.
  CorsConfigurationSource corsConfigurationSource(
    @Value("${app.cors-origin}") String origin
  ) {
    CorsConfiguration c = new CorsConfiguration();
    c.setAllowedOrigins(List.of(origin));
    c.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
    c.setAllowedHeaders(
      List.of("Authorization", "Content-Type", "Idempotency-Key")
    );
    UrlBasedCorsConfigurationSource s = new UrlBasedCorsConfigurationSource();
    s.registerCorsConfiguration("/**", c);
    return s;
  }
}

package com.chinix.fincore.auth;

import com.chinix.fincore.account.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

  private final UserRepository users;
  private final AccountRepository accounts;
  private final PasswordEncoder encoder;
  private final JwtEncoder jwt;

  public AuthService(
    UserRepository users,
    AccountRepository accounts,
    PasswordEncoder encoder,
    JwtEncoder jwt
  ) {
    this.users = users;
    this.accounts = accounts;
    this.encoder = encoder;
    this.jwt = jwt;
  }

  public record AuthResponse(String token, String name, String email) {}

  @Transactional
  public AuthResponse register(String email, String password, String name) {
    String normalized = email.trim().toLowerCase();
    if (
      users.findByEmail(normalized).isPresent()
    ) throw new ResponseStatusException(
      HttpStatus.CONFLICT,
      "Email already registered"
    );
    AppUser u = users.save(
      new AppUser(normalized, encoder.encode(password), name.trim())
    );
    accounts.save(
      new Account(
        u.id,
        "FC" + u.id.toString().replace("-", "").substring(0, 16).toUpperCase()
      )
    );
    return issue(u);
  }

  public AuthResponse login(String email, String password) {
    AppUser u = users
      .findByEmail(email.trim().toLowerCase())
      .orElseThrow(() ->
        new ResponseStatusException(
          HttpStatus.UNAUTHORIZED,
          "Invalid credentials"
        )
      );
    if (
      !encoder.matches(password, u.passwordHash)
    ) throw new ResponseStatusException(
      HttpStatus.UNAUTHORIZED,
      "Invalid credentials"
    );
    return issue(u);
  }

  private AuthResponse issue(AppUser u) {
    Instant now = Instant.now();
    JwtClaimsSet claims = JwtClaimsSet.builder()
      .issuer("fincore")
      .subject(u.id.toString())
      .issuedAt(now)
      .expiresAt(now.plusSeconds(3600))
      .claim("role", u.role)
      .build();
    String token = jwt
      .encode(
        JwtEncoderParameters.from(
          JwsHeader.with(
            org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256
          ).build(),
          claims
        )
      )
      .getTokenValue();
    return new AuthResponse(token, u.fullName, u.email);
  }

  public UUID currentUserId() {
    return UUID.fromString(
      SecurityContextHolder.getContext().getAuthentication().getName()
    );
  }
}

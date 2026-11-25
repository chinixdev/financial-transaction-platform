package com.chinix.fincore.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService service;

  public AuthController(AuthService service) {
    this.service = service;
  }

  public record Register(
    @Email @NotBlank String email,
    @NotBlank @Size(min = 10) String password,
    @NotBlank @Size(max = 160) String name
  ) {}

  public record Login(
    @Email @NotBlank String email,
    @NotBlank String password
  ) {}

  @PostMapping("/register")
  public AuthService.AuthResponse register(@Valid @RequestBody Register r) {
    return service.register(r.email(), r.password(), r.name());
  }

  @PostMapping("/login")
  public AuthService.AuthResponse login(@Valid @RequestBody Login r) {
    return service.login(r.email(), r.password());
  }
}

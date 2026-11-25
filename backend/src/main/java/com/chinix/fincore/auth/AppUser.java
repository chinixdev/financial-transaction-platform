package com.chinix.fincore.auth;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class AppUser {

  @Id
  public UUID id;

  @Column(nullable = false, unique = true)
  public String email;

  @Column(name = "password_hash", nullable = false)
  public String passwordHash;

  @Column(name = "full_name", nullable = false)
  public String fullName;

  @Column(nullable = false)
  public String role = "USER";

  protected AppUser() {}

  public AppUser(String email, String passwordHash, String fullName) {
    this.id = UUID.randomUUID();
    this.email = email;
    this.passwordHash = passwordHash;
    this.fullName = fullName;
  }
}

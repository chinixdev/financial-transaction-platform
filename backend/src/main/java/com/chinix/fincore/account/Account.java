package com.chinix.fincore.account;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "accounts")
public class Account {

  @Id
  public UUID id;

  @Column(name = "owner_id", nullable = false)
  public UUID ownerId;

  @Column(name = "account_number", nullable = false, unique = true)
  public String accountNumber;

  // La columna es CHAR(3): hay que decírselo a Hibernate para que la validación coincida.
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 3)
  public String currency = "PEN";

  @Column(nullable = false, precision = 19, scale = 2)
  public BigDecimal balance = BigDecimal.ZERO;

  public String status = "ACTIVE";

  protected Account() {}

  public Account(UUID ownerId, String number) {
    id = UUID.randomUUID();
    this.ownerId = ownerId;
    accountNumber = number;
  }
}

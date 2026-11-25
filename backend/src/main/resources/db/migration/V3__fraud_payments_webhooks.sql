-- Cuenta "mundo exterior": contrapartida contable de las recargas. Su saldo NO se modifica
-- (el dinero entra desde fuera); su posición se deduce del ledger. Nadie puede iniciar sesión con ella.
INSERT INTO app_users (id, email, password_hash, full_name, role)
VALUES ('00000000-0000-0000-0000-000000000001', 'system@fincore.local', '!disabled', 'System', 'SYSTEM');
INSERT INTO accounts (id, owner_id, account_number)
VALUES ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'FCSYSTEMEXTERNAL');

-- Alertas del motor antifraude (operaciones de riesgo medio/alto, permitidas o bloqueadas).
CREATE TABLE fraud_alerts (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_users(id),
  source_account_id UUID NOT NULL,
  amount NUMERIC(19,2) NOT NULL,
  score INT NOT NULL,
  level VARCHAR(10) NOT NULL,
  decision VARCHAR(10) NOT NULL,
  reasons VARCHAR(500) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX ix_fraud_user ON fraud_alerts(user_id, created_at DESC);

-- Recargas de saldo mediante una pasarela de pagos.
CREATE TABLE payments (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_users(id),
  account_id UUID NOT NULL REFERENCES accounts(id),
  amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
  currency VARCHAR(3) NOT NULL,
  provider VARCHAR(20) NOT NULL,
  provider_ref VARCHAR(100) UNIQUE,
  status VARCHAR(12) NOT NULL,
  idempotency_key VARCHAR(100) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  UNIQUE (user_id, idempotency_key)
);

-- Webhooks recibidos. event_id UNIQUE = un evento del proveedor se procesa una sola vez.
CREATE TABLE webhook_events (
  id UUID PRIMARY KEY,
  provider VARCHAR(20) NOT NULL,
  event_id VARCHAR(100) NOT NULL UNIQUE,
  type VARCHAR(60) NOT NULL,
  status VARCHAR(12) NOT NULL,
  payment_id UUID REFERENCES payments(id),
  received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

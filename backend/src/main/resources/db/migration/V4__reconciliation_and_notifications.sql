-- Diferencias detectadas al comparar nuestros pagos con los del proveedor.
CREATE TABLE reconciliation_findings (
  id UUID PRIMARY KEY,
  type VARCHAR(30) NOT NULL,
  provider_ref VARCHAR(100) NOT NULL,
  payment_id UUID REFERENCES payments(id),
  detail VARCHAR(300) NOT NULL,
  status VARCHAR(10) NOT NULL CHECK (status IN ('OPEN', 'RESOLVED')),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  resolved_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX ix_findings_status ON reconciliation_findings(status, created_at);

-- Notificaciones generadas al consumir eventos del outbox.
-- UNIQUE(event_id, user_id): si un evento se entrega dos veces, no se duplica la notificación.
CREATE TABLE notifications (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_users(id),
  event_id UUID NOT NULL,
  type VARCHAR(30) NOT NULL,
  message VARCHAR(200) NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  UNIQUE (event_id, user_id)
);
CREATE INDEX ix_notifications_user ON notifications(user_id, created_at DESC);

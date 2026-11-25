-- Reembolsos: un reembolso es una transferencia en sentido contrario ligada a la original.
ALTER TABLE transfers ADD COLUMN type VARCHAR(10) NOT NULL DEFAULT 'TRANSFER';
ALTER TABLE transfers ADD COLUMN original_transfer_id UUID REFERENCES transfers(id);
CREATE INDEX ix_transfers_original ON transfers(original_transfer_id);

-- Transactional Outbox: el evento se guarda en la MISMA transacción que la transferencia
-- y un proceso aparte lo publica después.
CREATE TABLE outbox_events (
  id UUID PRIMARY KEY,
  type VARCHAR(60) NOT NULL,
  payload VARCHAR(2000) NOT NULL,
  status VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
  attempts INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  processed_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX ix_outbox_status ON outbox_events(status, created_at);

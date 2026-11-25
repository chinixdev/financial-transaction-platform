import { demoApi } from "./demoApi";

export type Account = {
  id: string;
  accountNumber: string;
  currency: string;
  balance: number;
  status: string;
};
export type Transfer = {
  id: string;
  senderId: string;
  recipientId: string;
  amount: number;
  status: string; // COMPLETED | PARTIALLY_REFUNDED | REFUNDED
  type: string; // TRANSFER | REFUND
  reference: string | null;
  createdAt: string;
};
export type LedgerEntry = {
  id: string;
  transferId: string;
  accountId: string;
  direction: string;
  amount: number;
  createdAt: string;
};
export type Verification = {
  debits: number;
  credits: number;
  balanced: boolean;
};
export type Payment = {
  id: string;
  accountId: string;
  amount: number;
  provider: string;
  providerRef: string | null;
  status: string; // PENDING | SUCCEEDED | FAILED
  createdAt: string;
};
export type FraudAlert = {
  id: string;
  amount: number;
  score: number;
  level: string; // MEDIUM | HIGH
  decision: string; // ALLOWED | BLOCKED
  reasons: string;
  createdAt: string;
};
export type FraudRules = {
  maxAmount: number;
  maxPerWindow: number;
  windowMinutes: number;
  drainRatio: number;
  blockScore: number;
};
export type WebhookEvent = {
  id: string;
  eventId: string;
  type: string;
  status: string;
  paymentId: string | null;
  receivedAt: string;
};
export type Auth = { token: string; name: string; email: string; role: string };
export type Finding = {
  id: string;
  type: string; // STATUS_MISMATCH | MISSING_INTERNALLY | MISSING_AT_PROVIDER | AMOUNT_MISMATCH
  providerRef: string;
  detail: string;
  status: string;
  createdAt: string;
};
export type SystemHealth = {
  status: string;
  database: string;
  outboxPending: number;
  outboxFailed: number;
  paymentCircuit: string;
  pendingPayments: number;
  ledgerBalanced: boolean;
};
export type AppNotification = {
  id: string;
  type: string;
  message: string;
  createdAt: string;
};

const BASE = import.meta.env.VITE_API_URL || "http://localhost:8080/api";

// Por defecto se usa la API real. VITE_DEMO_MODE=true activa el Demo Mode: datos ficticios en
// LocalStorage, sin backend (pensado para desplegar solo el frontend en Vercel).
export const DEMO = import.meta.env.VITE_DEMO_MODE === "true";

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = sessionStorage.getItem("fc_token");
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...((options.headers as Record<string, string>) || {}),
  };
  let response: Response;
  try {
    response = await fetch(BASE + path, { ...options, headers });
  } catch {
    throw new Error(
      "No se pudo conectar con la API. Comprueba que el backend esté iniciado.",
    );
  }
  const data = await response.json().catch(() => ({}));
  if (!response.ok)
    throw new Error(data.error || `Error HTTP ${response.status}`);
  return data as T;
}

const realApi = {
  login: (email: string, password: string) =>
    request<Auth>("/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    }),
  register: (name: string, email: string, password: string) =>
    request<Auth>("/auth/register", {
      method: "POST",
      body: JSON.stringify({ name, email, password }),
    }),
  refund: (transferId: string, amount: number | null, key: string) =>
    request<{ id: string; status: string }>(
      `/transfers/${transferId}/refunds`,
      {
        method: "POST",
        headers: { "Idempotency-Key": key },
        body: JSON.stringify({ amount }),
      },
    ),
  ledger: () => request<LedgerEntry[]>("/ledger"),
  verifyLedger: () => request<Verification>("/ledger/verify"),
  payments: () => request<Payment[]>("/payments"),
  pay: (accountId: string, amount: number, key: string) =>
    request<Payment>("/payments", {
      method: "POST",
      headers: { "Idempotency-Key": key },
      body: JSON.stringify({ accountId, amount }),
    }),
  // Solo perfil local del backend: simula el webhook del proveedor para un pago.
  simulateWebhook: (paymentId: string) =>
    request<{ result: string }>(`/dev/payments/${paymentId}/simulate-webhook`, {
      method: "POST",
    }),
  alerts: () => request<FraudAlert[]>("/risk/alerts"),
  rules: () => request<FraudRules>("/risk/rules"),
  webhookEvents: () => request<WebhookEvent[]>("/webhooks/events"),
  notifications: () => request<AppNotification[]>("/notifications"),
  // Solo administradores
  findings: () => request<Finding[]>("/reconciliation/findings"),
  runReconciliation: () =>
    request<Finding[]>("/reconciliation/run", { method: "POST" }),
  resolveFinding: (id: string) =>
    request<Finding>(`/reconciliation/findings/${id}/resolve`, {
      method: "POST",
    }),
  systemHealth: () => request<SystemHealth>("/system/health"),
  requeueOutbox: () =>
    request<{ requeued: number }>("/system/outbox/requeue", { method: "POST" }),
  accounts: () => request<Account[]>("/accounts"),
  transfers: () => request<Transfer[]>("/transfers"),
  send: (
    sourceAccountId: string,
    destinationAccountNumber: string,
    amount: number,
    reference: string,
    key: string,
  ) =>
    request<{ id: string; status: string }>("/transfers", {
      method: "POST",
      headers: { "Idempotency-Key": key },
      body: JSON.stringify({
        sourceAccountId,
        destinationAccountNumber,
        amount,
        reference,
      }),
    }),
};

export const api: typeof realApi = DEMO ? (demoApi as typeof realApi) : realApi;

// Demo Mode: implementa la misma interfaz que la API real, pero sobre datos locales.
// Replica las reglas del backend (antifraude, idempotencia, reembolsos, webhooks,
// conciliación) para que la demo se comporte igual. No sustituye al backend: es solo para
// poder desplegar el frontend sin servidor. Mensajes de error en inglés, como el backend.
import type {
  Account,
  AppNotification,
  Auth,
  Finding,
  FraudAlert,
  FraudRules,
  LedgerEntry,
  Payment,
  SystemHealth,
  Transfer,
  Verification,
  WebhookEvent,
} from "./client";
import {
  db,
  DEMO_EMAIL,
  DEMO_PASSWORD,
  EXTERNAL,
  EXTERNAL_ACCOUNTS,
  resetDemo,
  save,
} from "./demoStore";

export { DEMO_EMAIL, DEMO_PASSWORD, resetDemo };

// Pequeña espera para que la interfaz se sienta como una API real (omitida en las pruebas).
const wait = (ms = 300) =>
  import.meta.env.MODE === "test"
    ? Promise.resolve()
    : new Promise((r) => setTimeout(r, ms));
const round = (n: number) => Math.round(n * 100) / 100;
const fail = (message: string): never => {
  throw new Error(message);
};

const RULES: FraudRules = {
  maxAmount: 5000,
  maxPerWindow: 5,
  windowMinutes: 10,
  drainRatio: 0.9,
  blockScore: 70,
};

// ---------- piezas internas ----------

/** Mismas reglas y pesos que FraudEngine del backend. */
function evaluate(amount: number, balance: number, recent: number) {
  let score = 0;
  const reasons: string[] = [];
  if (amount >= RULES.maxAmount) {
    score += 70;
    reasons.push("HIGH_AMOUNT");
  }
  if (recent >= RULES.maxPerWindow) {
    score += 40;
    reasons.push("VELOCITY");
  }
  if (balance > 0 && amount >= balance * RULES.drainRatio) {
    score += 30;
    reasons.push("DRAIN_BALANCE");
  }
  const level = score >= 70 ? "HIGH" : score >= 40 ? "MEDIUM" : "LOW";
  return { score, level, reasons, blocked: score >= RULES.blockScore };
}

function notify(type: string, message: string) {
  db().notifications.unshift({
    id: crypto.randomUUID(),
    type,
    message,
    createdAt: new Date().toISOString(),
  });
}

/** Guarda el movimiento y avisa a las cuentas propias involucradas. */
function record(
  from: string,
  to: string,
  amount: number,
  reference: string,
  type: string,
  original?: string,
) {
  const s = db();
  const transfer: Transfer = {
    id: crypto.randomUUID(),
    senderId: from,
    recipientId: to,
    amount,
    status: "COMPLETED",
    type,
    reference: original ? "Refund of " + original : reference,
    createdAt: new Date().toISOString(),
  };
  s.transfers.unshift(transfer);
  const mine = new Set(s.accounts.map((a) => a.id));
  const money = `S/ ${amount.toFixed(2)}`;
  if (type === "TOPUP")
    notify("TopUpCompleted", `Recarga acreditada: ${money}`);
  else if (type === "REFUND") {
    if (mine.has(from))
      notify("RefundCompleted", `Reembolso enviado: ${money}`);
    if (mine.has(to)) notify("RefundCompleted", `Reembolso recibido: ${money}`);
  } else {
    if (mine.has(from)) notify("TransferCompleted", `Enviaste ${money}`);
    if (mine.has(to)) notify("TransferCompleted", `Recibiste ${money}`);
  }
  return transfer;
}

/** Idempotencia: misma clave y contenido = mismo resultado; otro contenido = error. */
function replay(key: string, hash: string) {
  const previous = db().keys[key];
  if (!previous) return null;
  if (previous.hash !== hash)
    fail("Idempotency key reused with different payload");
  return { id: previous.id, status: previous.status };
}

function remember(key: string, hash: string, id: string, status: string) {
  db().keys[key] = { hash, id, status };
  save();
  return { id, status };
}

/** Acredita un pago pendiente una sola vez (recarga con contrapartida en el ledger). */
function complete(payment: Payment) {
  if (payment.status !== "PENDING") return;
  const account = db().accounts.find((a) => a.id === payment.accountId)!;
  account.balance = round(account.balance + payment.amount);
  record(EXTERNAL, account.id, payment.amount, "Top-up " + payment.id, "TOPUP");
  payment.status = "SUCCEEDED";
}

const isMoney = (n: number) => n > 0 && Math.round(n * 100) / 100 === n;

// ---------- API ----------

export const demoApi = {
  async login(email: string, password: string): Promise<Auth> {
    await wait();
    if (
      email.trim().toLowerCase() !== DEMO_EMAIL ||
      password !== DEMO_PASSWORD
    ) {
      fail(`Demo mode: use ${DEMO_EMAIL} / ${DEMO_PASSWORD}`);
    }
    return {
      token: "demo-token",
      name: "Ángel Demo",
      email: DEMO_EMAIL,
      role: "ADMIN",
    };
  },

  async register(name: string, email: string): Promise<Auth> {
    await wait();
    return { token: "demo-token", name, email, role: "USER" };
  },

  async accounts(): Promise<Account[]> {
    await wait(100);
    return db().accounts.map((a) => ({ ...a }));
  },

  async transfers(): Promise<Transfer[]> {
    await wait(100);
    return [...db().transfers].sort((a, b) =>
      b.createdAt.localeCompare(a.createdAt),
    );
  },

  async send(
    sourceId: string,
    destinationNumber: string,
    amount: number,
    reference: string,
    key: string,
  ) {
    await wait(450);
    const s = db();
    const hash = JSON.stringify([
      sourceId,
      destinationNumber,
      amount,
      reference,
    ]);
    const previous = replay(key, hash);
    if (previous) return previous;

    if (!isMoney(amount)) fail("Invalid amount");
    const source =
      s.accounts.find((a) => a.id === sourceId) ?? fail("Account not found");

    // Antifraude: se evalúa antes de mover dinero y la alerta queda guardada aunque se bloquee.
    const recent = s.transfers.filter(
      (t) =>
        t.senderId === source.id &&
        t.type === "TRANSFER" &&
        Date.now() - Date.parse(t.createdAt) < RULES.windowMinutes * 60_000,
    ).length;
    const risk = evaluate(amount, source.balance, recent);
    if (risk.level !== "LOW") {
      s.alerts.unshift({
        id: crypto.randomUUID(),
        amount,
        score: risk.score,
        level: risk.level,
        decision: risk.blocked ? "BLOCKED" : "ALLOWED",
        reasons: risk.reasons.join(","),
        createdAt: new Date().toISOString(),
      });
    }
    if (risk.blocked) {
      save();
      fail("Operation blocked by fraud rules: " + risk.reasons.join(","));
    }

    const number = destinationNumber.trim().toUpperCase();
    const own = s.accounts.find((a) => a.accountNumber === number);
    const destinationId =
      own?.id ?? EXTERNAL_ACCOUNTS[number]?.id ?? fail("Destination not found");
    if (destinationId === source.id) fail("Same account");
    if (source.balance < amount) fail("Insufficient funds");

    source.balance = round(source.balance - amount);
    if (own) own.balance = round(own.balance + amount);
    const transfer = record(
      source.id,
      destinationId,
      amount,
      reference,
      "TRANSFER",
    );
    return remember(key, hash, transfer.id, "COMPLETED");
  },

  /** Reembolso total o parcial. Solo quien recibió el dinero puede devolverlo (aquí: tus cuentas). */
  async refund(transferId: string, amount: number | null, key: string) {
    await wait(450);
    const s = db();
    const hash = JSON.stringify(["refund", transferId, amount]);
    const previous = replay(key, hash);
    if (previous) return previous;

    const original =
      s.transfers.find((t) => t.id === transferId) ??
      fail("Transfer not found");
    if (original.type !== "TRANSFER") fail("Only transfers can be refunded");
    const payer =
      s.accounts.find((a) => a.id === original.recipientId) ??
      fail("Not your account");
    const refunded = s.transfers
      .filter(
        (t) =>
          t.type === "REFUND" && t.reference === "Refund of " + original.id,
      )
      .reduce((sum, t) => sum + t.amount, 0);
    const remaining = round(original.amount - refunded);
    const value = amount ?? remaining;
    if (value > remaining)
      fail(`Refund exceeds the refundable amount (${remaining.toFixed(2)})`);
    if (!(value > 0)) fail("Nothing left to refund");
    if (payer.balance < value) fail("Insufficient funds");

    payer.balance = round(payer.balance - value);
    const receiver = s.accounts.find((a) => a.id === original.senderId);
    if (receiver) receiver.balance = round(receiver.balance + value);
    const refund = record(
      payer.id,
      original.senderId,
      value,
      "",
      "REFUND",
      original.id,
    );
    original.status = value === remaining ? "REFUNDED" : "PARTIALLY_REFUNDED";
    return remember(key, hash, refund.id, "COMPLETED");
  },

  /** El ledger se deduce de los movimientos: cada uno es un débito y un crédito. */
  async ledger(): Promise<LedgerEntry[]> {
    await wait(100);
    const s = db();
    const mine = new Set(s.accounts.map((a) => a.id));
    return s.transfers
      .flatMap((t) => [
        {
          id: t.id + "-d",
          transferId: t.id,
          accountId: t.senderId,
          direction: "DEBIT",
          amount: t.amount,
          createdAt: t.createdAt,
        },
        {
          id: t.id + "-c",
          transferId: t.id,
          accountId: t.recipientId,
          direction: "CREDIT",
          amount: t.amount,
          createdAt: t.createdAt,
        },
      ])
      .filter((e) => mine.has(e.accountId))
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  },

  async verifyLedger(): Promise<Verification> {
    await wait(50);
    const total = round(db().transfers.reduce((sum, t) => sum + t.amount, 0));
    return { debits: total, credits: total, balanced: true };
  },

  // --- Pagos y webhooks ---

  async payments(): Promise<Payment[]> {
    await wait(100);
    return [...db().payments].sort((a, b) =>
      b.createdAt.localeCompare(a.createdAt),
    );
  },

  /** Crea el pago en estado PENDING: se confirma con el webhook (o la conciliación). */
  async pay(accountId: string, amount: number, key: string): Promise<Payment> {
    await wait(450);
    const s = db();
    const hash = JSON.stringify(["pay", accountId, amount]);
    const previous = replay(key, hash);
    if (previous) return s.payments.find((p) => p.id === previous.id)!;

    if (!isMoney(amount)) fail("Invalid amount");
    if (!s.accounts.some((a) => a.id === accountId)) fail("Account not found");
    const payment: Payment = {
      id: crypto.randomUUID(),
      accountId,
      amount,
      provider: "MOCK",
      providerRef: "mock_pi_" + crypto.randomUUID(),
      status: "PENDING",
      createdAt: new Date().toISOString(),
    };
    s.payments.unshift(payment);
    remember(key, hash, payment.id, "PENDING");
    return payment;
  },

  /** Simula el webhook del proveedor. Pulsarlo dos veces demuestra la deduplicación. */
  async simulateWebhook(paymentId: string): Promise<{ result: string }> {
    await wait(300);
    const s = db();
    const payment =
      s.payments.find((p) => p.id === paymentId) ?? fail("Payment not found");
    const eventId = "evt_dev_" + payment.id;
    if (s.events.some((e) => e.eventId === eventId))
      return { result: "DUPLICATE" };
    complete(payment);
    s.events.unshift({
      id: crypto.randomUUID(),
      eventId,
      type: "payment_intent.succeeded",
      status: "PROCESSED",
      paymentId: payment.id,
      receivedAt: new Date().toISOString(),
    });
    save();
    return { result: "PROCESSED" };
  },

  async webhookEvents(): Promise<WebhookEvent[]> {
    await wait(100);
    return [...db().events];
  },

  // --- Riesgo y notificaciones ---

  async alerts(): Promise<FraudAlert[]> {
    await wait(100);
    return [...db().alerts];
  },

  async rules(): Promise<FraudRules> {
    return RULES;
  },

  async notifications(): Promise<AppNotification[]> {
    await wait(100);
    return [...db().notifications];
  },

  // --- Conciliación y estado (administrador) ---

  async findings(): Promise<Finding[]> {
    await wait(100);
    return db().findings.filter((f) => f.status === "OPEN");
  },

  /** El "proveedor" ya cobró los pagos pendientes: cada uno es un webhook perdido por corregir. */
  async runReconciliation(): Promise<Finding[]> {
    await wait(500);
    const s = db();
    for (const p of s.payments.filter((x) => x.status === "PENDING")) {
      const known = s.findings.some(
        (f) => f.status === "OPEN" && f.providerRef === p.providerRef,
      );
      if (!known) {
        s.findings.unshift({
          id: crypto.randomUUID(),
          type: "STATUS_MISMATCH",
          providerRef: p.providerRef!,
          detail: "Proveedor: succeeded / nosotros: PENDING",
          status: "OPEN",
          createdAt: new Date().toISOString(),
        });
      }
    }
    save();
    return db().findings.filter((f) => f.status === "OPEN");
  },

  async resolveFinding(findingId: string): Promise<Finding> {
    await wait(300);
    const s = db();
    const finding =
      s.findings.find((f) => f.id === findingId) ?? fail("Finding not found");
    if (finding.status === "RESOLVED") return finding;
    const payment = s.payments.find(
      (p) => p.providerRef === finding.providerRef,
    );
    if (finding.type !== "STATUS_MISMATCH" || payment?.status !== "PENDING")
      fail("Requires manual review");
    complete(payment!);
    finding.status = "RESOLVED";
    save();
    return finding;
  },

  async systemHealth(): Promise<SystemHealth> {
    await wait(100);
    return {
      status: "UP",
      database: "UP (LocalStorage)",
      outboxPending: 0,
      outboxFailed: 0,
      paymentCircuit: "CLOSED",
      pendingPayments: db().payments.filter((p) => p.status === "PENDING")
        .length,
      ledgerBalanced: true,
    };
  },

  async requeueOutbox(): Promise<{ requeued: number }> {
    return { requeued: 0 };
  },
};

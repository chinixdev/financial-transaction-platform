// Estado del Demo Mode: datos ficticios que viven en LocalStorage del navegador.
// No hay backend ni base de datos; "Restablecer datos" vuelve a la semilla inicial.
import type {
  Account,
  AppNotification,
  Finding,
  FraudAlert,
  Payment,
  Transfer,
  WebhookEvent,
} from "./client";

export const DEMO_EMAIL = "demo@fincore.dev";
export const DEMO_PASSWORD = "DemoFinCore2026";

/** Cuenta "mundo exterior": origen de las recargas (igual que en el backend). */
export const EXTERNAL = "00000000-0000-0000-0000-000000000002";

/** Cuentas de terceros a las que se puede transferir en la demo. */
export const EXTERNAL_ACCOUNTS: Record<string, { id: string; name: string }> = {
  FC900800700501: {
    id: "b2000000-0000-4000-8000-000000000001",
    name: "Acme Servicios SAC",
  },
  FC900800700502: {
    id: "b2000000-0000-4000-8000-000000000002",
    name: "Cowork Lima",
  },
  FC900800700503: {
    id: "b2000000-0000-4000-8000-000000000003",
    name: "Estudio Creativo Andes",
  },
};

export type State = {
  accounts: Account[];
  transfers: Transfer[];
  payments: Payment[];
  alerts: FraudAlert[];
  events: WebhookEvent[];
  notifications: AppNotification[];
  findings: Finding[];
  /** Claves de idempotencia ya usadas: clave -> resultado guardado. */
  keys: Record<string, { hash: string; id: string; status: string }>;
};

const STORAGE_KEY = "fincore-demo-v1";
const id = () => crypto.randomUUID();
const ago = (hours: number) =>
  new Date(Date.now() - hours * 3_600_000).toISOString();

function seed(): State {
  const [acme, cowork, estudio] = Object.values(EXTERNAL_ACCOUNTS).map(
    (a) => a.id,
  );
  const main = "a1000000-0000-4000-8000-000000000001";
  const savings = "a1000000-0000-4000-8000-000000000002";
  const equipment = id();
  const pay1 = id();
  const pay2 = id();

  const t = (
    senderId: string,
    recipientId: string,
    amount: number,
    reference: string,
    hours: number,
    extra: Partial<Transfer> = {},
  ): Transfer => ({
    id: extra.id ?? id(),
    senderId,
    recipientId,
    amount,
    status: "COMPLETED",
    type: "TRANSFER",
    reference,
    createdAt: ago(hours),
    ...extra,
  });

  return {
    accounts: [
      {
        id: main,
        accountNumber: "FC100200300401",
        currency: "PEN",
        balance: 4820.5,
        status: "ACTIVE",
      },
      {
        id: savings,
        accountNumber: "FC100200300402",
        currency: "PEN",
        balance: 1250,
        status: "ACTIVE",
      },
    ],
    transfers: [
      t(acme, main, 1500, "Pago de factura #1042", 6),
      t(main, cowork, 320.75, "Alquiler de coworking", 30),
      t(main, savings, 500, "Ahorro mensual", 55),
      t(estudio, savings, 750, "Servicio de diseño", 80),
      t(savings, acme, 89.9, "Suscripción cloud", 120),
      t(main, cowork, 200, "Compra de equipos", 150, {
        id: equipment,
        status: "PARTIALLY_REFUNDED",
      }),
      t(cowork, main, 60, "Refund of " + equipment, 140, { type: "REFUND" }),
      t(EXTERNAL, main, 1000, "Top-up " + pay1, 200, { type: "TOPUP" }),
    ],
    payments: [
      {
        id: pay1,
        accountId: main,
        amount: 1000,
        provider: "MOCK",
        providerRef: "mock_pi_seed_1",
        status: "SUCCEEDED",
        createdAt: ago(200),
      },
      // Pendiente a propósito: se puede confirmar con "Simular webhook" o con la conciliación.
      {
        id: pay2,
        accountId: main,
        amount: 250,
        provider: "MOCK",
        providerRef: "mock_pi_seed_2",
        status: "PENDING",
        createdAt: ago(1),
      },
    ],
    alerts: [
      {
        id: id(),
        amount: 8000,
        score: 100,
        level: "HIGH",
        decision: "BLOCKED",
        reasons: "HIGH_AMOUNT,DRAIN_BALANCE",
        createdAt: ago(48),
      },
      {
        id: id(),
        amount: 40,
        score: 40,
        level: "MEDIUM",
        decision: "ALLOWED",
        reasons: "VELOCITY",
        createdAt: ago(26),
      },
    ],
    events: [
      {
        id: id(),
        eventId: "evt_demo_seed_1",
        type: "payment_intent.succeeded",
        status: "PROCESSED",
        paymentId: pay1,
        receivedAt: ago(200),
      },
    ],
    notifications: [
      {
        id: id(),
        type: "TransferCompleted",
        message: "Recibiste S/ 1500.00",
        createdAt: ago(6),
      },
      {
        id: id(),
        type: "TransferCompleted",
        message: "Enviaste S/ 320.75",
        createdAt: ago(30),
      },
      {
        id: id(),
        type: "TopUpCompleted",
        message: "Recarga acreditada: S/ 1000.00",
        createdAt: ago(200),
      },
    ],
    // Un cobro que el proveedor tiene y nosotros no: requiere revisión manual.
    findings: [
      {
        id: id(),
        type: "MISSING_INTERNALLY",
        providerRef: "pi_ghost_demo",
        detail: "Cobro del proveedor por 99.00 sin pago interno",
        status: "OPEN",
        createdAt: ago(3),
      },
    ],
    keys: {},
  };
}

function load(): State {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) return JSON.parse(raw) as State;
  } catch {
    // sin LocalStorage (modo privado, SSR, pruebas): se trabaja en memoria
  }
  return seed();
}

// Carga perezosa: en modo real (sin demo) este módulo nunca toca LocalStorage.
let state: State | null = null;

export const db = () => (state ??= load());

export function save() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
    // si no se puede guardar, la demo sigue funcionando en memoria
  }
}

/** Vuelve a los datos iniciales y los guarda. */
export function resetDemo() {
  state = seed();
  save();
}

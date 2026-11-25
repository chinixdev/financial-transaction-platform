// Modo DEMO: simula el backend en memoria para que el portfolio funcione
// sin servidor ni base de datos. Los datos se reinician al recargar la página.
import type { Account, Auth, Transfer } from "./client";

const delay = (ms = 350) => new Promise((r) => setTimeout(r, ms));
const ago = (hours: number) =>
  new Date(Date.now() - hours * 3_600_000).toISOString();

export const DEMO_EMAIL = "demo@fincore.dev";
export const DEMO_PASSWORD = "DemoFinCore2026";

const accounts: Account[] = [
  {
    id: "a1000000-0000-4000-8000-000000000001",
    accountNumber: "FC100200300401",
    currency: "PEN",
    balance: 4820.5,
    status: "ACTIVE",
  },
  {
    id: "a1000000-0000-4000-8000-000000000002",
    accountNumber: "FC100200300402",
    currency: "PEN",
    balance: 1250.0,
    status: "ACTIVE",
  },
];

// Cuentas de terceros a las que se puede transferir en la demo.
const externalAccounts: Record<string, string> = {
  FC900800700501: "b2000000-0000-4000-8000-000000000001",
  FC900800700502: "b2000000-0000-4000-8000-000000000002",
  FC900800700503: "b2000000-0000-4000-8000-000000000003",
};

const transfers: Transfer[] = [
  {
    id: "e5a1c3d2-1111-4000-8000-000000000001",
    senderId: "b2000000-0000-4000-8000-000000000001",
    recipientId: accounts[0].id,
    amount: 1500,
    status: "COMPLETED",
    reference: "Pago de factura #1042",
    createdAt: ago(6),
  },
  {
    id: "e5a1c3d2-2222-4000-8000-000000000002",
    senderId: accounts[0].id,
    recipientId: "b2000000-0000-4000-8000-000000000002",
    amount: 320.75,
    status: "COMPLETED",
    reference: "Alquiler de coworking",
    createdAt: ago(30),
  },
  {
    id: "e5a1c3d2-3333-4000-8000-000000000003",
    senderId: accounts[0].id,
    recipientId: accounts[1].id,
    amount: 500,
    status: "COMPLETED",
    reference: "Ahorro mensual",
    createdAt: ago(55),
  },
  {
    id: "e5a1c3d2-4444-4000-8000-000000000004",
    senderId: "b2000000-0000-4000-8000-000000000003",
    recipientId: accounts[1].id,
    amount: 750,
    status: "COMPLETED",
    reference: "Servicio de diseño",
    createdAt: ago(80),
  },
  {
    id: "e5a1c3d2-5555-4000-8000-000000000005",
    senderId: accounts[1].id,
    recipientId: "b2000000-0000-4000-8000-000000000001",
    amount: 89.9,
    status: "COMPLETED",
    reference: "Suscripción cloud",
    createdAt: ago(120),
  },
];

const processedKeys = new Map<string, { id: string; status: string }>();

export const demoApi = {
  async login(email: string, password: string): Promise<Auth> {
    await delay();
    if (
      email.trim().toLowerCase() !== DEMO_EMAIL ||
      password !== DEMO_PASSWORD
    ) {
      throw new Error(`Modo demo: usa ${DEMO_EMAIL} / ${DEMO_PASSWORD}`);
    }
    return { token: "demo-token", name: "Ángel Demo", email: DEMO_EMAIL };
  },

  async register(name: string, email: string): Promise<Auth> {
    await delay();
    return { token: "demo-token", name, email };
  },

  async accounts(): Promise<Account[]> {
    await delay(150);
    return accounts.map((a) => ({ ...a }));
  },

  async transfers(): Promise<Transfer[]> {
    await delay(150);
    return [...transfers].sort((a, b) =>
      b.createdAt.localeCompare(a.createdAt),
    );
  },

  async send(
    sourceAccountId: string,
    destinationAccountNumber: string,
    amount: number,
    reference: string,
    key: string,
  ) {
    await delay(500);
    const previous = processedKeys.get(key);
    if (previous) return previous; // idempotencia: misma clave, mismo resultado

    const source = accounts.find((a) => a.id === sourceAccountId);
    if (!source) throw new Error("Cuenta de origen no encontrada.");
    const dest = destinationAccountNumber.trim().toUpperCase();
    const ownDest = accounts.find((a) => a.accountNumber === dest);
    const destId = ownDest?.id ?? externalAccounts[dest];
    if (!destId)
      throw new Error(
        `Cuenta de destino no encontrada. Prueba con ${Object.keys(externalAccounts)[0]}`,
      );
    if (destId === source.id)
      throw new Error("La cuenta de origen y destino no pueden ser la misma.");
    if (!(amount > 0)) throw new Error("El importe debe ser mayor que cero.");
    if (amount > source.balance) throw new Error("Saldo insuficiente.");

    source.balance = Math.round((source.balance - amount) * 100) / 100;
    if (ownDest)
      ownDest.balance = Math.round((ownDest.balance + amount) * 100) / 100;
    const id = crypto.randomUUID();
    transfers.push({
      id,
      senderId: source.id,
      recipientId: destId,
      amount,
      status: "COMPLETED",
      reference: reference || null,
      createdAt: new Date().toISOString(),
    });
    const result = { id, status: "COMPLETED" };
    processedKeys.set(key, result);
    return result;
  },
};

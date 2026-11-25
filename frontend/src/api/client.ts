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
  status: string;
  reference: string | null;
  createdAt: string;
};
export type Auth = { token: string; name: string; email: string };

const BASE = import.meta.env.VITE_API_URL || "http://localhost:8080/api";

// Modo demo (sin backend) por defecto. Para usar el backend real define
// VITE_DEMO=false al compilar o ejecutar (ver .env.example).
export const DEMO = import.meta.env.VITE_DEMO !== "false";

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

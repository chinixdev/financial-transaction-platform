import { beforeEach, describe, expect, it, vi } from "vitest";

// LocalStorage de mentira (las pruebas corren en Node, sin navegador).
const memory = new Map<string, string>();
vi.stubGlobal("localStorage", {
  getItem: (k: string) => memory.get(k) ?? null,
  setItem: (k: string, v: string) => void memory.set(k, String(v)),
  removeItem: (k: string) => void memory.delete(k),
});

import { demoApi, DEMO_EMAIL, DEMO_PASSWORD, resetDemo } from "./demoApi";

const key = () => crypto.randomUUID();
const MAIN = "a1000000-0000-4000-8000-000000000001";
const SAVINGS = "a1000000-0000-4000-8000-000000000002";
const COWORK = "FC900800700502";
const balance = async (id: string) =>
  (await demoApi.accounts()).find((a) => a.id === id)!.balance;

beforeEach(() => {
  memory.clear();
  resetDemo();
});

describe("acceso", () => {
  it("solo acepta las credenciales demo y devuelve rol admin", async () => {
    await expect(demoApi.login("otro@x.com", "xxxxxxxxxxxx")).rejects.toThrow(
      "Demo mode",
    );
    expect((await demoApi.login(DEMO_EMAIL, DEMO_PASSWORD)).role).toBe("ADMIN");
  });
});

describe("transferencias", () => {
  it("mueve el dinero, registra el movimiento y notifica", async () => {
    const before = await balance(MAIN);
    await demoApi.send(MAIN, COWORK, 100, "prueba", key());
    expect(await balance(MAIN)).toBe(before - 100);
    expect((await demoApi.transfers())[0].reference).toBe("prueba");
    expect((await demoApi.notifications())[0].message).toBe(
      "Enviaste S/ 100.00",
    );
  });

  it("idempotencia: misma clave = un solo cobro; otro contenido = error", async () => {
    const k = key();
    const before = await balance(MAIN);
    const first = await demoApi.send(MAIN, COWORK, 50, "x", k);
    const second = await demoApi.send(MAIN, COWORK, 50, "x", k);
    expect(second.id).toBe(first.id);
    expect(await balance(MAIN)).toBe(before - 50);
    await expect(demoApi.send(MAIN, COWORK, 51, "x", k)).rejects.toThrow(
      "different payload",
    );
  });

  it("rechaza saldo insuficiente, destino inexistente e importes inválidos", async () => {
    await expect(
      demoApi.send(SAVINGS, COWORK, 1300, "x", key()),
    ).rejects.toThrow(/Insufficient|blocked/);
    await expect(demoApi.send(MAIN, "FC000", 10, "x", key())).rejects.toThrow(
      "Destination not found",
    );
    await expect(demoApi.send(MAIN, COWORK, -5, "x", key())).rejects.toThrow(
      "Invalid amount",
    );
    await expect(demoApi.send(MAIN, COWORK, 1.999, "x", key())).rejects.toThrow(
      "Invalid amount",
    );
  });

  it("el ledger de la demo siempre está balanceado", async () => {
    await demoApi.send(MAIN, COWORK, 10, "x", key());
    expect((await demoApi.verifyLedger()).balanced).toBe(true);
  });
});

describe("antifraude", () => {
  it("bloquea un importe atípico sin mover dinero y guarda la alerta", async () => {
    const before = await balance(MAIN);
    const alertsBefore = (await demoApi.alerts()).length;
    await expect(demoApi.send(MAIN, COWORK, 5000, "x", key())).rejects.toThrow(
      "blocked by fraud rules: HIGH_AMOUNT",
    );
    expect(await balance(MAIN)).toBe(before);
    expect((await demoApi.alerts()).length).toBe(alertsBefore + 1);
    expect((await demoApi.alerts())[0].decision).toBe("BLOCKED");
  });

  it("ráfaga + vaciado de saldo se bloquea; una operación pequeña solo genera alerta", async () => {
    for (let i = 0; i < 5; i++)
      await demoApi.send(SAVINGS, COWORK, 1, "x", key());
    const left = await balance(SAVINGS); // 1245
    await expect(
      demoApi.send(SAVINGS, COWORK, left, "x", key()),
    ).rejects.toThrow("VELOCITY,DRAIN_BALANCE");
    await demoApi.send(SAVINGS, COWORK, 1, "x", key());
    expect((await demoApi.alerts())[0]).toMatchObject({
      level: "MEDIUM",
      decision: "ALLOWED",
    });
  });
});

describe("reembolsos", () => {
  const sendToOwn = async (amount: number) =>
    (await demoApi.send(MAIN, "FC100200300402", amount, "interna", key())).id;

  it("parciales y total restauran saldos; no se puede exceder el original", async () => {
    const mainBefore = await balance(MAIN);
    const id = await sendToOwn(100);
    await demoApi.refund(id, 30, key());
    await expect(demoApi.refund(id, 80, key())).rejects.toThrow("exceeds");
    await demoApi.refund(id, null, key()); // el resto: 70
    expect(await balance(MAIN)).toBe(mainBefore);
    expect((await demoApi.transfers()).find((t) => t.id === id)!.status).toBe(
      "REFUNDED",
    );
  });

  it("un reembolso repetido con la misma clave se aplica una sola vez", async () => {
    const id = await sendToOwn(100);
    const k = key();
    const a = await demoApi.refund(id, 10, k);
    const b = await demoApi.refund(id, 10, k);
    expect(b.id).toBe(a.id);
    expect(
      (await demoApi.transfers()).filter(
        (t) => t.type === "REFUND" && t.reference === "Refund of " + id,
      ),
    ).toHaveLength(1);
  });
});

describe("pagos, webhooks y conciliación", () => {
  it("el pago queda PENDING y el webhook lo acredita una sola vez", async () => {
    const before = await balance(MAIN);
    const payment = await demoApi.pay(MAIN, 80, key());
    expect(payment.status).toBe("PENDING");
    expect(await balance(MAIN)).toBe(before);

    expect((await demoApi.simulateWebhook(payment.id)).result).toBe(
      "PROCESSED",
    );
    expect((await demoApi.simulateWebhook(payment.id)).result).toBe(
      "DUPLICATE",
    );
    expect(await balance(MAIN)).toBe(before + 80);
    expect((await demoApi.verifyLedger()).balanced).toBe(true);
  });

  it("la conciliación detecta pagos pendientes y los corrige una sola vez", async () => {
    const before = await balance(MAIN);
    const found = (await demoApi.runReconciliation()).filter(
      (f) => f.type === "STATUS_MISMATCH",
    );
    expect(found.length).toBeGreaterThan(0); // el pago semilla pendiente (S/ 250)

    await demoApi.resolveFinding(found[0].id);
    await demoApi.resolveFinding(found[0].id); // repetir no acredita de nuevo
    expect(await balance(MAIN)).toBe(before + 250);
  });

  it("las diferencias sin corrección automática piden revisión manual", async () => {
    const ghost = (await demoApi.findings()).find(
      (f) => f.type === "MISSING_INTERNALLY",
    )!;
    await expect(demoApi.resolveFinding(ghost.id)).rejects.toThrow(
      "manual review",
    );
  });
});

describe("persistencia y reinicio", () => {
  it("los cambios sobreviven a una recarga y 'restablecer' vuelve a la semilla", async () => {
    const initial = await balance(MAIN);
    await demoApi.send(MAIN, COWORK, 25, "x", key());

    // Simula recargar la página: se vuelve a importar el módulo y lee LocalStorage.
    vi.resetModules();
    const reloaded = await import("./demoApi");
    expect(
      (await reloaded.demoApi.accounts()).find((a) => a.id === MAIN)!.balance,
    ).toBe(initial - 25);

    reloaded.resetDemo();
    expect(
      (await reloaded.demoApi.accounts()).find((a) => a.id === MAIN)!.balance,
    ).toBe(initial);
  });

  it("el estado del sistema refleja los pagos pendientes", async () => {
    const health = await demoApi.systemHealth();
    expect(health).toMatchObject({
      status: "UP",
      paymentCircuit: "CLOSED",
      pendingPayments: 1,
    });
  });
});

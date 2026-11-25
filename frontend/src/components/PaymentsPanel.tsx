import { useState } from "react";
import { api, type Account, type Payment } from "../api/client";
import { useLoad } from "../hooks/useLoad";
import { useSubmit } from "../hooks/useSubmit";
import { money } from "../utils";
import { Badge, dateTime, ErrorBox, Field, Panel, Table } from "./ui";

type Props = { accounts: Account[]; refreshKey: number; onChanged: () => void };

// Recarga de saldo mediante la pasarela de pagos (Stripe en modo prueba o proveedor simulado).
export default function PaymentsPanel({
  accounts,
  refreshKey,
  onChanged,
}: Props) {
  const { data: payments, reload } = useLoad(
    api.payments,
    [] as Payment[],
    refreshKey,
  );
  const [accountId, setAccountId] = useState("");
  const [amount, setAmount] = useState("");
  // Una clave por intento: si el usuario reintenta tras un fallo de red, no se cobra dos veces.
  const [key, setKey] = useState(() => crypto.randomUUID());
  const { busy, error, run } = useSubmit();

  function submit(e: React.FormEvent) {
    e.preventDefault();
    void run(async () => {
      await api.pay(accountId || accounts[0].id, Number(amount), key);
      setAmount("");
      setKey(crypto.randomUUID());
      reload();
      onChanged();
    });
  }

  function simulate(id: string) {
    void run(async () => {
      await api.simulateWebhook(id);
      reload();
      onChanged();
    });
  }

  return (
    <>
      <Panel
        className="table-panel"
        title="Recargar saldo"
        subtitle="El pago se crea en la pasarela y se acredita al confirmarse (webhook)"
      >
        <form onSubmit={submit} className="inline-form">
          <label>
            Cuenta
            <select
              value={accountId}
              onChange={(e) => setAccountId(e.target.value)}
            >
              {accounts.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.accountNumber} · {money(a.balance)}
                </option>
              ))}
            </select>
          </label>
          <Field
            label="Importe (PEN)"
            required
            type="number"
            min="0.01"
            step="0.01"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
          />
          <button className="primary" disabled={busy || accounts.length === 0}>
            {busy ? "Procesando..." : "Recargar"}
          </button>
        </form>
        <ErrorBox message={error} />
      </Panel>

      <Panel
        className="table-panel"
        title="Pagos"
        subtitle="Historial de recargas"
      >
        <Table
          rows={payments}
          empty="Aún no hay pagos."
          columns={[
            { head: "FECHA", cell: (p) => dateTime(p.createdAt) },
            { head: "PROVEEDOR", cell: (p) => p.provider },
            { head: "REFERENCIA", cell: (p) => p.providerRef ?? "—" },
            { head: "ESTADO", cell: (p) => <Badge value={p.status} /> },
            { head: "IMPORTE", right: true, cell: (p) => money(p.amount) },
            {
              head: "ACCIÓN",
              cell: (p) =>
                p.status === "PENDING" && (
                  <button className="outline" onClick={() => simulate(p.id)}>
                    Simular webhook
                  </button>
                ),
            },
          ]}
        />
      </Panel>
    </>
  );
}

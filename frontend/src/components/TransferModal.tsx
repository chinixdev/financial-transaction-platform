import { useState } from "react";
import { ArrowRight, ShieldCheck, X } from "lucide-react";
import { api, type Account } from "../api/client";
import { useSubmit } from "../hooks/useSubmit";
import { money } from "../utils";
import { ErrorBox, Field } from "./ui";

type Props = { accounts: Account[]; onClose: () => void; onDone: () => void };

export default function TransferModal({ accounts, onClose, onDone }: Props) {
  const [source, setSource] = useState(accounts[0]?.id || "");
  const [dest, setDest] = useState("");
  const [amount, setAmount] = useState("");
  const [reference, setReference] = useState("");
  // Una clave por apertura del modal: si se reintenta, no se duplica la transferencia.
  const [key] = useState(() => crypto.randomUUID());
  const { busy, error, run } = useSubmit();

  function submit(e: React.FormEvent) {
    e.preventDefault();
    void run(async () => {
      await api.send(source, dest, Number(amount), reference, key);
      onDone();
    });
  }

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div className="modal" onMouseDown={(e) => e.stopPropagation()}>
        <div className="modal-title">
          <div>
            <div className="eyebrow">OPERACIÓN FINANCIERA</div>
            <h2>Nueva transferencia</h2>
          </div>
          <button className="icon-btn" onClick={onClose}>
            <X size={20} />
          </button>
        </div>

        <form onSubmit={submit}>
          <label>
            Cuenta de origen
            <select
              required
              value={source}
              onChange={(e) => setSource(e.target.value)}
            >
              {accounts.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.accountNumber} · {money(a.balance)}
                </option>
              ))}
            </select>
          </label>
          <Field
            label="Número de cuenta de destino"
            required
            value={dest}
            placeholder="FC..."
            onChange={(e) => setDest(e.target.value)}
          />
          <Field
            label="Importe (PEN)"
            required
            type="number"
            min="0.01"
            step="0.01"
            value={amount}
            placeholder="0.00"
            onChange={(e) => setAmount(e.target.value)}
          />
          <Field
            label="Referencia (opcional)"
            value={reference}
            maxLength={200}
            placeholder="Motivo de la transferencia"
            onChange={(e) => setReference(e.target.value)}
          />

          <ErrorBox message={error} />
          <div className="modal-note">
            <ShieldCheck size={17} /> Operación atómica y protegida contra
            envíos duplicados.
          </div>
          <button
            disabled={busy || accounts.length === 0}
            className="primary auth-submit"
          >
            {busy ? "Procesando..." : "Confirmar transferencia"}{" "}
            <ArrowRight size={17} />
          </button>
        </form>
      </div>
    </div>
  );
}

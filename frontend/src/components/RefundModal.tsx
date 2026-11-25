import { useState } from "react";
import { ArrowRight, X } from "lucide-react";
import { api, type Transfer } from "../api/client";
import { useSubmit } from "../hooks/useSubmit";
import { money } from "../utils";
import { ErrorBox, Field } from "./ui";

type Props = { transfer: Transfer; onClose: () => void; onDone: () => void };

export default function RefundModal({ transfer, onClose, onDone }: Props) {
  const [amount, setAmount] = useState("");
  const [key] = useState(() => crypto.randomUUID());
  const { busy, error, run } = useSubmit();

  function submit(e: React.FormEvent) {
    e.preventDefault();
    // Importe vacío = reembolso total de lo pendiente. El backend valida el máximo.
    void run(async () => {
      await api.refund(transfer.id, amount ? Number(amount) : null, key);
      onDone();
    });
  }

  return (
    <div className="modal-backdrop" onMouseDown={onClose}>
      <div className="modal" onMouseDown={(e) => e.stopPropagation()}>
        <div className="modal-title">
          <div>
            <div className="eyebrow">REEMBOLSO</div>
            <h2>Devolver {money(transfer.amount)}</h2>
          </div>
          <button className="icon-btn" onClick={onClose}>
            <X size={20} />
          </button>
        </div>
        <form onSubmit={submit}>
          <Field
            label="Importe a devolver (vacío = total pendiente)"
            type="number"
            min="0.01"
            step="0.01"
            value={amount}
            placeholder="Total"
            onChange={(e) => setAmount(e.target.value)}
          />
          <ErrorBox message={error} />
          <button disabled={busy} className="primary auth-submit">
            {busy ? "Procesando..." : "Confirmar reembolso"}{" "}
            <ArrowRight size={17} />
          </button>
        </form>
      </div>
    </div>
  );
}

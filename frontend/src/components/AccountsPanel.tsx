import { CreditCard, LockKeyhole, Wallet } from "lucide-react";
import type { Account } from "../api/client";
import { money } from "../utils";
import { Panel } from "./ui";

export default function AccountsPanel({ accounts }: { accounts: Account[] }) {
  return (
    <Panel
      className="accounts-panel"
      title="Tus cuentas"
      subtitle="Disponibles para operar"
      right={<Wallet size={18} className="muted" />}
    >
      {accounts.map((a) => (
        <div className="account-item" key={a.id}>
          <div className="account-logo">
            <CreditCard size={19} />
          </div>
          <div>
            <strong>Cuenta PEN</strong>
            <small>{a.accountNumber}</small>
          </div>
          <div className="account-amount">
            {money(a.balance)}
            <small>{a.status}</small>
          </div>
        </div>
      ))}
      {accounts.length === 0 && (
        <div className="empty">No hay cuentas disponibles.</div>
      )}
      <div className="info-strip">
        <LockKeyhole size={16} /> Transferencias protegidas con validaciones
        transaccionales.
      </div>
    </Panel>
  );
}

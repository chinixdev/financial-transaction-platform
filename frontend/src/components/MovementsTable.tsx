import { useState } from "react";
import {
  Activity,
  ArrowDownLeft,
  ArrowRight,
  ArrowUpRight,
  CheckCircle2,
  Search,
} from "lucide-react";
import type { Account, Transfer } from "../api/client";
import { money } from "../utils";
import { Panel } from "./ui";

type Props = {
  transfers: Transfer[];
  accounts: Account[];
  onRefresh: () => void;
};

export default function MovementsTable({
  transfers,
  accounts,
  onRefresh,
}: Props) {
  const [search, setSearch] = useState("");
  const myIds = new Set(accounts.map((a) => a.id));
  const text = search.toLowerCase();
  const rows = transfers.filter(
    (t) =>
      (t.reference || "").toLowerCase().includes(text) || t.id.includes(search),
  );

  return (
    <Panel
      className="table-panel"
      title="Movimientos recientes"
      subtitle="Historial de tus operaciones"
      right={
        <div className="table-actions">
          <div className="search">
            <Search size={16} />
            <input
              placeholder="Buscar movimiento..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
          <button className="outline" onClick={onRefresh}>
            Actualizar <ArrowRight size={15} />
          </button>
        </div>
      }
    >
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>OPERACIÓN</th>
              <th>REFERENCIA</th>
              <th>FECHA</th>
              <th>ESTADO</th>
              <th className="right">IMPORTE</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((t) => {
              const out = myIds.has(t.senderId); // salió de una cuenta mía
              return (
                <tr key={t.id}>
                  <td>
                    <div className="operation">
                      <div className={"operation-icon " + (out ? "out" : "in")}>
                        {out ? (
                          <ArrowUpRight size={18} />
                        ) : (
                          <ArrowDownLeft size={18} />
                        )}
                      </div>
                      <div>
                        <strong>
                          {out
                            ? "Transferencia enviada"
                            : "Transferencia recibida"}
                        </strong>
                        <small>#{t.id.slice(0, 8).toUpperCase()}</small>
                      </div>
                    </div>
                  </td>
                  <td>{t.reference || "Sin referencia"}</td>
                  <td>{new Date(t.createdAt).toLocaleString("es-PE")}</td>
                  <td>
                    <span className="success-badge">
                      <CheckCircle2 size={13} /> {t.status}
                    </span>
                  </td>
                  <td
                    className={"right amount " + (out ? "negative" : "positive")}
                  >
                    {out ? "−" : "+"}
                    {money(t.amount)}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        {rows.length === 0 && (
          <div className="empty">
            <Activity size={27} />
            <strong>Aún no hay movimientos</strong>
            <span>Cuando realices una transferencia aparecerá aquí.</span>
          </div>
        )}
      </div>
    </Panel>
  );
}

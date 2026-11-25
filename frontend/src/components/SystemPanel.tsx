import { CheckCircle2, Database, Layers, Zap } from "lucide-react";
import { api, type SystemHealth } from "../api/client";
import { useLoad } from "../hooks/useLoad";
import { useSubmit } from "../hooks/useSubmit";
import { Badge, ErrorBox, Panel, StatCard } from "./ui";

// Estado operativo: base de datos, cola de eventos (outbox), circuit breaker y ledger.
export default function SystemPanel({ refreshKey }: { refreshKey: number }) {
  const { data: h, reload } = useLoad(
    api.systemHealth,
    null as SystemHealth | null,
    refreshKey,
  );
  const { busy, error, run } = useSubmit();

  const requeue = () =>
    void run(async () => {
      await api.requeueOutbox();
      reload();
    });

  if (!h)
    return (
      <Panel
        className="table-panel"
        title="Estado del sistema"
        subtitle="Cargando..."
      >
        <ErrorBox message={error} />
      </Panel>
    );

  return (
    <>
      <div className="stats">
        <StatCard
          featured
          label="Estado general"
          value={h.status}
          icon={<CheckCircle2 size={20} />}
          note={<>Base de datos: {h.database}</>}
        />
        <StatCard
          label="Eventos pendientes"
          value={String(h.outboxPending)}
          icon={<Layers size={20} />}
          note={<>Cola de errores: {h.outboxFailed}</>}
        />
        <StatCard
          label="Pagos pendientes"
          value={String(h.pendingPayments)}
          icon={<Database size={20} />}
          note={<>Esperando confirmación</>}
        />
      </div>
      <Panel
        className="table-panel"
        title="Componentes"
        subtitle="Resiliencia e integridad contable"
        right={
          <button
            className="outline"
            disabled={busy || h.outboxFailed === 0}
            onClick={requeue}
          >
            <Zap size={14} /> Reintentar eventos fallidos
          </button>
        }
      >
        <div className="info-strip">
          Circuit breaker de la pasarela:{" "}
          <Badge
            value={h.paymentCircuit === "OPEN" ? "FAILED" : h.paymentCircuit}
          />{" "}
          · Ledger:{" "}
          <Badge value={h.ledgerBalanced ? "BALANCEADO" : "DESCUADRADO"} />
        </div>
        <ErrorBox message={error} />
      </Panel>
    </>
  );
}

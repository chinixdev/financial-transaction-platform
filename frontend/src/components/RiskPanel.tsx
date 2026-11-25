import { api, type FraudAlert, type FraudRules } from "../api/client";
import { useLoad } from "../hooks/useLoad";
import { money } from "../utils";
import { Badge, dateTime, Panel, Table } from "./ui";

// Reglas vigentes y alertas del motor antifraude.
export default function RiskPanel({ refreshKey }: { refreshKey: number }) {
  const { data: alerts } = useLoad(api.alerts, [] as FraudAlert[], refreshKey);
  const { data: rules } = useLoad(api.rules, null as FraudRules | null);

  return (
    <>
      {rules && (
        <Panel
          className="table-panel"
          title="Reglas activas"
          subtitle={`Se bloquea una operación con puntaje ≥ ${rules.blockScore}`}
        >
          <div className="info-strip">
            HIGH_AMOUNT: importe ≥ {money(rules.maxAmount)} (+70) · VELOCITY: ≥{" "}
            {rules.maxPerWindow} transferencias en {rules.windowMinutes} min
            (+40) · DRAIN_BALANCE: envía ≥ {Math.round(rules.drainRatio * 100)}%
            del saldo (+30)
          </div>
        </Panel>
      )}
      <Panel
        className="table-panel"
        title="Alertas de riesgo"
        subtitle="Operaciones de riesgo medio o alto"
      >
        <Table
          rows={alerts}
          empty="Sin alertas: no se detectaron operaciones sospechosas."
          columns={[
            { head: "FECHA", cell: (a) => dateTime(a.createdAt) },
            { head: "IMPORTE", cell: (a) => money(a.amount) },
            { head: "PUNTAJE", cell: (a) => a.score },
            { head: "RIESGO", cell: (a) => <Badge value={a.level} /> },
            { head: "REGLAS", cell: (a) => a.reasons },
            { head: "DECISIÓN", cell: (a) => <Badge value={a.decision} /> },
          ]}
        />
      </Panel>
    </>
  );
}

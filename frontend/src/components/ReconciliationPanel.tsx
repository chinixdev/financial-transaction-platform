import { api, type Finding } from "../api/client";
import { useLoad } from "../hooks/useLoad";
import { useSubmit } from "../hooks/useSubmit";
import { Badge, dateTime, ErrorBox, Panel, Table } from "./ui";

// Conciliación: compara nuestros pagos con los del proveedor (solo administradores).
export default function ReconciliationPanel({
  refreshKey,
}: {
  refreshKey: number;
}) {
  const { data: findings, reload } = useLoad(
    api.findings,
    [] as Finding[],
    refreshKey,
  );
  const { busy, error, run } = useSubmit();

  const execute = () =>
    void run(async () => {
      await api.runReconciliation();
      reload();
    });

  const resolve = (id: string) =>
    void run(async () => {
      await api.resolveFinding(id);
      reload();
    });

  return (
    <Panel
      className="table-panel"
      title="Conciliación de pagos"
      subtitle="Diferencias entre nuestros registros y los del proveedor"
      right={
        <button className="primary" disabled={busy} onClick={execute}>
          {busy ? "Procesando..." : "Ejecutar conciliación"}
        </button>
      }
    >
      <ErrorBox message={error} />
      <Table
        rows={findings}
        empty="Sin diferencias abiertas: nuestros registros coinciden con el proveedor."
        columns={[
          { head: "FECHA", cell: (f) => dateTime(f.createdAt) },
          { head: "TIPO", cell: (f) => <Badge value={f.type} /> },
          { head: "REFERENCIA", cell: (f) => f.providerRef },
          { head: "DETALLE", cell: (f) => f.detail },
          {
            head: "ACCIÓN",
            cell: (f) =>
              f.type === "STATUS_MISMATCH" ? (
                <button className="outline" onClick={() => resolve(f.id)}>
                  Corregir
                </button>
              ) : (
                "Revisión manual"
              ),
          },
        ]}
      />
    </Panel>
  );
}

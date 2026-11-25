import { api, type WebhookEvent } from "../api/client";
import { useLoad } from "../hooks/useLoad";
import { Badge, dateTime, Panel, Table } from "./ui";

// Eventos recibidos del proveedor de pagos (firma verificada y sin duplicados).
export default function WebhooksPanel({ refreshKey }: { refreshKey: number }) {
  const { data: events } = useLoad(
    api.webhookEvents,
    [] as WebhookEvent[],
    refreshKey,
  );

  return (
    <Panel
      className="table-panel"
      title="Webhooks recibidos"
      subtitle="Cada evento se verifica por firma HMAC y se procesa una sola vez"
    >
      <Table
        rows={events}
        empty="Aún no se han recibido webhooks. Crea un pago y simula su confirmación."
        columns={[
          { head: "FECHA", cell: (e) => dateTime(e.receivedAt) },
          { head: "EVENTO", cell: (e) => e.eventId.slice(0, 24) },
          { head: "TIPO", cell: (e) => e.type },
          { head: "ESTADO", cell: (e) => <Badge value={e.status} /> },
        ]}
      />
    </Panel>
  );
}

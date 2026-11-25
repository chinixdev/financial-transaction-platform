import { api, type AppNotification } from "../api/client";
import { useLoad } from "../hooks/useLoad";
import { dateTime, Panel, Table } from "./ui";

// Notificaciones generadas al consumir los eventos del outbox.
export default function NotificationsPanel({
  refreshKey,
}: {
  refreshKey: number;
}) {
  const { data: items } = useLoad(
    api.notifications,
    [] as AppNotification[],
    refreshKey,
  );

  return (
    <Panel
      className="table-panel"
      title="Notificaciones"
      subtitle="Generadas de forma asíncrona a partir de los eventos del sistema"
    >
      <Table
        rows={items}
        empty="Sin notificaciones todavía."
        columns={[
          { head: "FECHA", cell: (n) => dateTime(n.createdAt) },
          { head: "EVENTO", cell: (n) => n.type },
          { head: "MENSAJE", cell: (n) => n.message },
        ]}
      />
    </Panel>
  );
}

import { api, type LedgerEntry, type Verification } from "../api/client";
import { useLoad } from "../hooks/useLoad";
import { money } from "../utils";
import { Badge, dateTime, Panel, Table } from "./ui";

// Libro contable: cada movimiento es un DÉBITO y un CRÉDITO por el mismo importe.
export default function LedgerPanel({ refreshKey }: { refreshKey: number }) {
  const { data: entries } = useLoad(
    api.ledger,
    [] as LedgerEntry[],
    refreshKey,
  );
  const { data: check } = useLoad(
    api.verifyLedger,
    null as Verification | null,
    refreshKey,
  );

  return (
    <Panel
      className="table-panel"
      title="Libro contable (Ledger)"
      subtitle="Doble entrada: débitos y créditos siempre suman lo mismo"
      right={
        check && (
          <span>
            <Badge value={check.balanced ? "BALANCEADO" : "DESCUADRADO"} />{" "}
            {money(check.debits)} = {money(check.credits)}
          </span>
        )
      }
    >
      <Table
        rows={entries}
        empty="Aún no hay asientos contables."
        columns={[
          { head: "FECHA", cell: (e) => dateTime(e.createdAt) },
          {
            head: "TRANSFERENCIA",
            cell: (e) => "#" + e.transferId.slice(0, 8).toUpperCase(),
          },
          {
            head: "CUENTA",
            cell: (e) => e.accountId.slice(0, 8).toUpperCase(),
          },
          {
            head: "TIPO",
            cell: (e) => (e.direction === "DEBIT" ? "Débito" : "Crédito"),
          },
          {
            head: "IMPORTE",
            right: true,
            cell: (e) => (
              <span
                className={
                  "amount " +
                  (e.direction === "DEBIT" ? "negative" : "positive")
                }
              >
                {e.direction === "DEBIT" ? "−" : "+"}
                {money(e.amount)}
              </span>
            ),
          },
        ]}
      />
    </Panel>
  );
}

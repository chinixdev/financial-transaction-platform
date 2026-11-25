// Piezas visuales pequeñas que se reutilizan en toda la app.
import type { ReactNode } from "react";
import { Activity } from "lucide-react";

export function Brand() {
  return (
    <div className="brand">
      <div className="brand-icon">
        <Activity size={23} />
      </div>
      <div>
        <strong>
          fincore<span>.</span>
        </strong>
        <small>FINANCIAL PLATFORM</small>
      </div>
    </div>
  );
}

// Campo de formulario: etiqueta + input. Recibe cualquier prop de <input>.
export function Field({
  label,
  ...input
}: { label: string } & React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <label>
      {label}
      <input {...input} />
    </label>
  );
}

// Tarjeta con encabezado (título, subtítulo y algo a la derecha).
export function Panel({
  title,
  subtitle,
  right,
  className,
  children,
}: {
  title: string;
  subtitle: string;
  right?: ReactNode;
  className: string;
  children: ReactNode;
}) {
  return (
    <section className={"panel " + className}>
      <div className="panel-heading">
        <div>
          <h2>{title}</h2>
          <p>{subtitle}</p>
        </div>
        {right}
      </div>
      {children}
    </section>
  );
}

export function StatCard({
  label,
  value,
  icon,
  note,
  featured,
}: {
  label: string;
  value: string;
  icon: ReactNode;
  note: ReactNode;
  featured?: boolean;
}) {
  return (
    <div className={"stat" + (featured ? " stat-featured" : "")}>
      <div className="stat-head">
        <span>{label}</span>
        {icon}
      </div>
      <div className="stat-value">{value}</div>
      <div className="stat-foot">{note}</div>
      {featured && <div className="stat-decoration" />}
    </div>
  );
}

export function ErrorBox({ message }: { message: string }) {
  return message ? <div className="error">{message}</div> : null;
}

// Tabla genérica: define las columnas una vez y se reutiliza en todos los módulos.
export type Column<T> = {
  head: string;
  cell: (row: T) => ReactNode;
  right?: boolean;
};

export function Table<T extends { id: string }>({
  columns,
  rows,
  empty,
}: {
  columns: Column<T>[];
  rows: T[];
  empty: string;
}) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c.head} className={c.right ? "right" : ""}>
                {c.head}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.id}>
              {columns.map((c) => (
                <td key={c.head} className={c.right ? "right" : ""}>
                  {c.cell(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      {rows.length === 0 && <div className="empty">{empty}</div>}
    </div>
  );
}

// Etiqueta de estado: roja si es un bloqueo/fallo, ámbar si está pendiente o en riesgo medio.
export function Badge({ value }: { value: string }) {
  const bad = ["BLOCKED", "FAILED", "HIGH", "DESCUADRADO"].includes(value);
  const warn = ["PENDING", "MEDIUM", "IGNORED"].includes(value);
  const style = bad
    ? { color: "#f87171", background: "#3b1d1d" }
    : warn
      ? { color: "#fbbf24", background: "#3a2f14" }
      : undefined;
  return (
    <span className="success-badge" style={style}>
      {value}
    </span>
  );
}

export const dateTime = (iso: string) =>
  iso ? new Date(iso).toLocaleString("es-PE") : "—";

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

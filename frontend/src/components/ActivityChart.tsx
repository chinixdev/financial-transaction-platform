import { ChevronDown } from "lucide-react";
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
} from "recharts";
import { Panel } from "./ui";

// Datos ilustrativos (no vienen del backend).
const trend = [
  { day: "Lun", v: 280 },
  { day: "Mar", v: 390 },
  { day: "Mié", v: 340 },
  { day: "Jue", v: 540 },
  { day: "Vie", v: 460 },
  { day: "Sáb", v: 710 },
  { day: "Dom", v: 620 },
];

export default function ActivityChart() {
  return (
    <Panel
      className="chart-panel"
      title="Actividad financiera"
      subtitle="Vista de referencia · datos ilustrativos"
      right={
        <span className="tag">
          Últimos 7 días <ChevronDown size={14} />
        </span>
      }
    >
      <div className="chart-value">
        S/ 3,340.00 <span>Ejemplo visual</span>
      </div>
      <div className="chart">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={trend}>
            <defs>
              <linearGradient id="fill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="#2dd4a1" stopOpacity={0.24} />
                <stop offset="100%" stopColor="#2dd4a1" stopOpacity={0} />
              </linearGradient>
            </defs>
            <CartesianGrid
              stroke="#22372f"
              vertical={false}
              strokeDasharray="3 6"
            />
            <XAxis
              dataKey="day"
              tickLine={false}
              axisLine={false}
              tick={{ fill: "#7f9c91", fontSize: 12 }}
              dy={12}
            />
            <Tooltip
              contentStyle={{
                background: "#13241f",
                border: "1px solid #315246",
                borderRadius: 12,
                color: "#fff",
              }}
            />
            <Area
              type="monotone"
              dataKey="v"
              stroke="#38d9a9"
              strokeWidth={3}
              fill="url(#fill)"
            />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </Panel>
  );
}

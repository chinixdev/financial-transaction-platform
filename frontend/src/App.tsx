import { useEffect, useState } from "react";
import {
  ArrowUpRight,
  Bell,
  CheckCircle2,
  ChevronDown,
  CircleHelp,
  CreditCard,
  LayoutDashboard,
  LogOut,
  Menu,
  Plus,
  Send,
  ShieldCheck,
  TrendingUp,
  Wallet,
} from "lucide-react";
import { api, type Account, type Auth, type Transfer } from "./api/client";
import AccountsPanel from "./components/AccountsPanel";
import ActivityChart from "./components/ActivityChart";
import AuthScreen from "./components/AuthScreen";
import MovementsTable from "./components/MovementsTable";
import TransferModal from "./components/TransferModal";
import { Brand, StatCard } from "./components/ui";
import { money } from "./utils";

const MENU = [
  { name: "Resumen", icon: LayoutDashboard },
  { name: "Mis cuentas", icon: Wallet },
  { name: "Transferencias", icon: ArrowUpRight },
  { name: "Seguridad", icon: ShieldCheck },
];

const pad = (n: number) => n.toString().padStart(2, "0");

export default function App() {
  const [token, setToken] = useState(sessionStorage.getItem("fc_token"));
  const [name, setName] = useState(
    sessionStorage.getItem("fc_name") || "Usuario",
  );
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [transfers, setTransfers] = useState<Transfer[]>([]);
  const [error, setError] = useState("");
  const [view, setView] = useState("Resumen");
  const [modal, setModal] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);

  async function refresh() {
    try {
      const [a, t] = await Promise.all([api.accounts(), api.transfers()]);
      setAccounts(a);
      setTransfers(t);
      setError("");
    } catch (e) {
      setError((e as Error).message);
    }
  }

  useEffect(() => {
    if (token) void refresh();
  }, [token]);

  function login(a: Auth) {
    sessionStorage.setItem("fc_token", a.token);
    sessionStorage.setItem("fc_name", a.name);
    setName(a.name);
    setToken(a.token);
  }

  function logout() {
    sessionStorage.clear();
    setToken(null);
    setAccounts([]);
    setTransfers([]);
  }

  if (!token) return <AuthScreen onAuth={login} />;

  const total = accounts.reduce((sum, a) => sum + Number(a.balance), 0);
  const active = accounts.filter((a) => a.status === "ACTIVE").length;
  const isHome = view === "Resumen";

  return (
    <div className="shell">
      <aside className={"sidebar " + (menuOpen ? "opened" : "")}>
        <Brand />
        <div className="side-label">WORKSPACE</div>
        <nav>
          {MENU.map((item) => (
            <button
              key={item.name}
              className={"nav " + (view === item.name ? "active" : "")}
              onClick={() => {
                setView(item.name);
                setMenuOpen(false);
              }}
            >
              <item.icon size={18} />
              {item.name}
              {view === item.name && <span className="nav-dot" />}
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="secure">
            <ShieldCheck size={19} />
            <div>
              <strong>Entorno seguro</strong>
              <small>Operaciones simuladas</small>
            </div>
          </div>
          <button className="nav" onClick={logout}>
            <LogOut size={18} />
            Cerrar sesión
          </button>
        </div>
      </aside>

      <div className="main">
        <header className="topbar">
          <button
            className="icon-btn mobile-toggle"
            onClick={() => setMenuOpen(!menuOpen)}
          >
            <Menu size={21} />
          </button>
          <div className="breadcrumb">
            Workspace <span>/</span> <strong>{view}</strong>
          </div>
          <div className="top-actions">
            <span className="env">
              <span /> DEMO ENVIRONMENT
            </span>
            <button className="icon-btn" title="Notificaciones">
              <Bell size={19} />
            </button>
            <div className="avatar">{name.slice(0, 1).toUpperCase()}</div>
            <div className="identity">
              <strong>{name}</strong>
              <small>Cuenta personal</small>
            </div>
            <ChevronDown size={15} />
          </div>
        </header>

        <main className="content">
          <div className="heading">
            <div>
              <div className="eyebrow">
                <span className="live-dot" /> FINANCIAL OVERVIEW
              </div>
              <h1>{isHome ? "Tu dinero, bajo control." : view}</h1>
              <p>
                {isHome
                  ? "Gestiona tus cuentas y transferencias desde un solo lugar."
                  : "Consulta y administra tus operaciones financieras."}
              </p>
            </div>
            <button className="primary" onClick={() => setModal(true)}>
              <Plus size={17} /> Nueva transferencia
            </button>
          </div>

          {error && (
            <div className="error">
              {error}
              <button onClick={() => void refresh()}>Reintentar</button>
            </div>
          )}

          <div className="stats">
            <StatCard
              featured
              label="Balance total"
              value={money(total)}
              icon={<Wallet size={20} />}
              note={
                <>
                  <span className="chip">PEN</span> Saldo disponible
                </>
              }
            />
            <StatCard
              label="Cuentas activas"
              value={pad(active)}
              icon={<CreditCard size={20} />}
              note={
                <>
                  <span className="mini-green">
                    <CheckCircle2 size={13} /> Activas
                  </span>{" "}
                  Cuentas personales
                </>
              }
            />
            <StatCard
              label="Transferencias"
              value={pad(transfers.length)}
              icon={<Send size={20} />}
              note={
                <>
                  <span className="mini-green">
                    <TrendingUp size={13} /> Registradas
                  </span>{" "}
                  Últimos movimientos
                </>
              }
            />
          </div>

          <div className="columns">
            <ActivityChart />
            <AccountsPanel accounts={accounts} />
          </div>

          <MovementsTable
            transfers={transfers}
            accounts={accounts}
            onRefresh={() => void refresh()}
          />

          <footer>
            © 2026 FinCore · Proyecto de ingeniería financiera{" "}
            <span>
              <CircleHelp size={14} /> Plataforma de simulación · Sin dinero
              real
            </span>
          </footer>
        </main>
      </div>

      {modal && (
        <TransferModal
          accounts={accounts}
          onClose={() => setModal(false)}
          onDone={() => {
            setModal(false);
            void refresh();
          }}
        />
      )}
    </div>
  );
}

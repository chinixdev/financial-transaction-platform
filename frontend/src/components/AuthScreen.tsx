import { useState } from "react";
import { ArrowRight, LockKeyhole, ShieldCheck } from "lucide-react";
import { api, DEMO, type Auth } from "../api/client";
import { DEMO_EMAIL, DEMO_PASSWORD } from "../api/demoApi";
import { useSubmit } from "../hooks/useSubmit";
import { Brand, ErrorBox, Field } from "./ui";

export default function AuthScreen({ onAuth }: { onAuth: (a: Auth) => void }) {
  const [register, setRegister] = useState(false);
  const [name, setName] = useState("");
  const [email, setEmail] = useState(DEMO ? DEMO_EMAIL : "");
  const [password, setPassword] = useState(DEMO ? DEMO_PASSWORD : "");
  const { busy, error, setError, run } = useSubmit();

  function submit(e: React.FormEvent) {
    e.preventDefault();
    void run(async () => {
      onAuth(
        register
          ? await api.register(name, email, password)
          : await api.login(email, password),
      );
    });
  }

  return (
    <div className="auth-page">
      <div className="auth-art">
        <Brand />
        <div className="auth-message">
          <div className="eyebrow">THE FUTURE OF FINANCE</div>
          <h1>
            Finanzas más claras.
            <br />
            <em>Decisiones más inteligentes.</em>
          </h1>
          <p>
            Una experiencia financiera segura y pensada para controlar cada
            movimiento.
          </p>
          <div className="auth-feature">
            <ShieldCheck /> Arquitectura transaccional segura
          </div>
        </div>
        <div className="auth-art-footer">
          SIMULATED FINANCIAL ENVIRONMENT · 2026
        </div>
      </div>

      <div className="auth-form-side">
        <form className="auth-form" onSubmit={submit}>
          <div className="eyebrow">BIENVENIDO A FINCORE</div>
          <h2>{register ? "Crea tu cuenta" : "Inicia sesión"}</h2>
          <p>Accede a tu espacio financiero.</p>

          {register && (
            <Field
              label="Nombre completo"
              required
              value={name}
              placeholder="Tu nombre"
              onChange={(e) => setName(e.target.value)}
            />
          )}
          <Field
            label="Correo electrónico"
            type="email"
            required
            value={email}
            placeholder="nombre@ejemplo.com"
            onChange={(e) => setEmail(e.target.value)}
          />
          <Field
            label="Contraseña"
            type="password"
            required
            value={password}
            minLength={register ? 10 : undefined}
            placeholder="••••••••••"
            onChange={(e) => setPassword(e.target.value)}
          />

          {DEMO && (
            <div className="modal-note">
              <ShieldCheck size={17} /> Modo demo: datos simulados, sin backend.
            </div>
          )}
          <ErrorBox message={error} />

          <button className="primary auth-submit" disabled={busy}>
            {busy
              ? "Procesando..."
              : register
                ? "Crear cuenta"
                : "Acceder a mi cuenta"}
            <ArrowRight size={17} />
          </button>

          <div className="auth-switch">
            {register ? "¿Ya tienes una cuenta?" : "¿Aún no tienes una cuenta?"}{" "}
            <button
              type="button"
              onClick={() => {
                setRegister(!register);
                setError("");
              }}
            >
              {register ? "Iniciar sesión" : "Registrarme"}
            </button>
          </div>
          <div className="auth-disclaimer">
            <LockKeyhole size={14} /> Proyecto demostrativo · No utiliza dinero
            real
          </div>
        </form>
      </div>
    </div>
  );
}

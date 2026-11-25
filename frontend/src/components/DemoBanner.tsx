import { FlaskConical, RotateCcw } from "lucide-react";
import { resetDemo } from "../api/demoApi";

// Aviso visible en todo momento: la demo no usa backend ni dinero real.
export default function DemoBanner() {
  function reset() {
    if (!confirm("¿Restablecer los datos de la demo a su estado inicial?"))
      return;
    resetDemo();
    sessionStorage.clear();
    window.location.reload();
  }

  return (
    <div className="demo-banner">
      <span>
        <FlaskConical size={15} /> <strong>MODO DEMO</strong> · datos ficticios
        guardados en tu navegador · sin backend ni dinero real
      </span>
      <button onClick={reset}>
        <RotateCcw size={13} /> Restablecer datos
      </button>
    </div>
  );
}

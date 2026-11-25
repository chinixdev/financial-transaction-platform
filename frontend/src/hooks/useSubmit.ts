import { useState } from "react";

// Maneja el patrón "enviar formulario": estado de carga + mensaje de error.
// Lo usan el login/registro y el modal de transferencias.
export function useSubmit() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError("");
    try {
      await action();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return { busy, error, setError, run };
}

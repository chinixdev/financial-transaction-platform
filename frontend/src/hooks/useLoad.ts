import { useCallback, useEffect, useState } from "react";

// Carga datos de la API al montar y cuando cambia `refreshKey`. Devuelve también `reload`.
export function useLoad<T>(load: () => Promise<T>, initial: T, refreshKey = 0) {
  const [data, setData] = useState<T>(initial);
  const [error, setError] = useState("");

  const reload = useCallback(() => {
    load()
      .then((d) => {
        setData(d);
        setError("");
      })
      .catch((e: Error) => setError(e.message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(reload, [reload, refreshKey]);
  return { data, error, reload };
}

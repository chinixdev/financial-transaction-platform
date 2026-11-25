// Formatea un número como moneda peruana: 1500 -> "S/ 1,500.00"
export const money = (n: number) =>
  new Intl.NumberFormat("es-PE", { style: "currency", currency: "PEN" }).format(
    Number(n),
  );

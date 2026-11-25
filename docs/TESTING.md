# Plan de pruebas

## Prueba manual mínima

1. Registrar usuario A y B. Verificar que cada uno ve solo su cuenta.
2. Financiar la cuenta A en PostgreSQL **solo en desarrollo** siguiendo README.
3. Transferir 25.50 desde A hacia B; comprobar saldos y registros en `transfers` y `ledger_entries`.
4. Repetir POST con la misma `Idempotency-Key` y el mismo payload: devuelve el mismo ID.
5. Repetir con la misma clave pero importe distinto: debe responder 409.
6. Probar saldo insuficiente, cuenta inexistente, transferencia a la misma cuenta y JWT inválido.
7. Probar que A no puede enviar dinero desde la cuenta de B.

## Pendiente antes de declarar el sistema robusto

- Pruebas de integración con PostgreSQL real y Testcontainers.
- Carrera de dos solicitudes simultáneas sobre una misma cuenta.
- Reintento concurrente con misma clave, verificando que solo existe una transferencia.
- Fallo intencional antes de persistir ledger y comprobación de rollback.
- Pruebas de validación y autorización con MockMvc.

**Nota:** los tests incluidos son solo un smoke test de JUnit; no representan cobertura transaccional. No se ha ejecutado una suite de integración en este entorno.

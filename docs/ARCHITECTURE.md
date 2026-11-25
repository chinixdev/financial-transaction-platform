# Arquitectura y decisiones

## Componentes

- **Frontend React + TypeScript**: sesión en `sessionStorage`, consumo de API y visualización de cuentas/transferencias. El token no es persistente entre pestañas y requiere protección XSS rigurosa; una versión endurecida usaría BFF/cookies HttpOnly.
- **Backend Spring Boot**: paquetes `auth`, `account`, `transfer`, `config`, `common`; endpoints REST con JWT HS256.
- **PostgreSQL**: Flyway para esquema versionado, constraints, transacciones ACID y `PESSIMISTIC_WRITE`.

## Flujo de transferencia

1. JWT identifica al actor (el frontend nunca envía `userId`).
2. Se valida la petición y la clave de idempotencia.
3. Se comprueba el destino y se bloquean ambas cuentas por orden UUID para reducir deadlocks.
4. Se verifica titularidad, estado, moneda y saldo.
5. En una sola transacción se actualizan los saldos, se crea la transferencia y se registran dos entradas de ledger (DEBIT/CREDIT).
6. Se guarda la relación clave → transferencia. Un fallo revierte todo.

## Invariantes

- `balance >= 0` a nivel SQL.
- No se permite enviar a la misma cuenta.
- Cada transferencia confirmada produce un débito y un crédito del mismo importe.
- El usuario solo puede operar desde sus propias cuentas.
- Idempotencia por `(user_id, request_key)`.

## Consideraciones de evolución

El balance almacenado es una proyección mutable y el ledger es inmutable a nivel de aplicación, no protegido aún contra modificaciones directas en SQL. Para un sistema financiero serio se necesitan cuentas contables de contrapartida para fondeo, reconciliación, controles operativos, auditoría, pruebas de concurrencia automatizadas y un modelo de estados para transferencias externas.

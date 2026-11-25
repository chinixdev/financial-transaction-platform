# FinCore — Plataforma de transferencias financieras

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4-6DB33F)
![React](https://img.shields.io/badge/React-19-61DAFB)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791)

Simulador de cuentas y transferencias entre usuarios, con una interfaz en tonos verdes. Es un proyecto de portfolio centrado en lo difícil de los sistemas transaccionales: **transferencias atómicas, concurrencia segura, ledger de doble entrada y reintentos idempotentes**. No maneja dinero real.

> **Demo online:** funciona solo con el frontend y datos simulados (sin backend), así que carga al instante.
> Usuario: `demo@fincore.dev` · Contraseña: `DemoFinCore2026` (ya vienen escritos en el login).
> Para probar una transferencia, usa la cuenta destino `FC900800700501`.

## Problema que resuelve

En un sistema de pagos, los casos difíciles no son el "camino feliz":

- Dos transferencias simultáneas sobre la misma cuenta no deben gastar dos veces el mismo saldo.
- Si el cliente reintenta una petición por un fallo de red, no debe duplicar el cobro.
- Dos transferencias en sentidos opuestos (A→B y B→A) no deben bloquearse entre sí.
- Cada movimiento debe quedar registrado para poder auditarlo.

## Funcionalidades

- **Registro e inicio de sesión** con JWT (válido 1 hora) y contraseñas con BCrypt.
- **Cuentas**: cada usuario recibe una cuenta en PEN al registrarse.
- **Transferencias** entre cuentas, con validación de saldo, moneda y estado.
- **Idempotencia**: cabecera `Idempotency-Key`; repetir la petición devuelve la transferencia original y cambiar el contenido con la misma clave devuelve `409`.
- **Ledger de doble entrada**: cada transferencia genera un débito y un crédito.
- **Dashboard**: saldo total, cuentas, historial con búsqueda y modal de nueva transferencia.

## Tecnologías

| Área | Tecnología |
|---|---|
| Backend | Java 21, Spring Boot 3.4, Spring Security (OAuth2 Resource Server, JWT HS256) |
| Datos | Spring Data JPA, PostgreSQL 16, migraciones con Flyway |
| Frontend | React 19, TypeScript, Vite, Recharts, lucide-react |
| Infraestructura | Docker Compose |

## Decisiones técnicas

- **Transacción ACID.** Saldos, transferencia, asientos del ledger y clave de idempotencia se guardan en una sola transacción (`@Transactional`): o se guarda todo o nada.
- **Bloqueo pesimista ordenado.** Las dos cuentas se bloquean siempre en orden de ID, lo que evita deadlocks entre transferencias cruzadas.
- **Idempotencia con hash.** Se guarda un SHA-256 del contenido junto a la clave; sirve para distinguir un reintento legítimo de un abuso de la clave.
- **Restricciones en la base de datos.** `balance >= 0`, `amount > 0` y claves únicas actúan como última línea de defensa aunque falle el código.
- **Modo demo en el frontend.** `src/api/demoApi.ts` imita al backend en memoria; con `VITE_DEMO=false` se usa la API real.

## Estructura

```
fincore/
├── backend/            API Spring Boot
│   └── src/main/java/com/chinix/fincore/
│       ├── auth/       registro, login y JWT
│       ├── account/    cuentas
│       ├── transfer/   transferencias, ledger e idempotencia
│       └── config/     seguridad y CORS
├── frontend/           SPA React
│   └── src/
│       ├── api/        cliente HTTP y API demo
│       ├── components/ pantallas y piezas reutilizables
│       └── hooks/      lógica compartida de formularios
├── docs/               arquitectura, roadmap y pruebas
└── docker-compose.yml  PostgreSQL + backend
```

## Cómo ejecutarlo

### Solo el frontend (modo demo, sin instalar nada más)

Requiere Node.js 20+.

```bash
cd frontend
npm install
npm run dev
```

Abre <http://localhost:5173>.

### Proyecto completo (backend + base de datos)

Requiere Docker Desktop y Node.js 20+.

```bash
# 1. PostgreSQL + backend (puerto 8080)
docker compose up --build -d

# 2. Frontend apuntando a la API real
cd frontend
cp .env.example .env      # descomenta VITE_DEMO=false
npm install
npm run dev
```

Registra una cuenta en <http://localhost:5173>. Para probar transferencias necesitas una segunda cuenta (otro correo) y saldo, ver más abajo.

### Fondos de prueba (solo desarrollo)

Las cuentas nacen con saldo cero y no hay endpoint de recarga. Para pruebas locales:

```bash
docker compose exec postgres psql -U fincore -d fincore -c \
  "UPDATE accounts SET balance = 1000.00 WHERE account_number = 'FC_TU_CUENTA';"
```

## API

| Método | Ruta | Acceso | Descripción |
|---|---|---|---|
| POST | `/api/auth/register` | Pública | Crea usuario y cuenta PEN |
| POST | `/api/auth/login` | Pública | Devuelve un JWT |
| GET | `/api/accounts` | JWT | Cuentas del usuario |
| GET | `/api/transfers` | JWT | Últimos 30 movimientos |
| POST | `/api/transfers` | JWT + `Idempotency-Key` | Crea una transferencia |
| GET | `/actuator/health` | Pública | Estado de la aplicación |

```http
POST /api/transfers
Authorization: Bearer <TOKEN>
Idempotency-Key: 9d3d17ac-5d9f-4d58-bb4c-efbb92d8f2ac
Content-Type: application/json

{"sourceAccountId":"UUID-DE-TU-CUENTA","destinationAccountNumber":"FC123...","amount":25.50,"reference":"Prueba"}
```

## Estado y alcance

Es una **V1 centrada en el núcleo transaccional**. La gráfica del dashboard usa datos ilustrativos. No incluye roles administrativos, mecanismo de fondeo con contrapartida contable, límites por operación, mensajería ni observabilidad distribuida. No debe usarse en producción.

**Estado de validación:** el frontend compila y se probó en modo demo. El backend está implementado, pero no se ha compilado ni probado de extremo a extremo en el entorno donde se preparó esta versión.

**Limitación de concurrencia:** dos solicitudes simultáneas con la misma clave de idempotencia pueden producir un `409`; el cliente debe reintentar con la misma clave para recuperar el resultado.

## Documentación

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md): componentes, invariantes y decisiones.
- [`docs/ROADMAP.md`](docs/ROADMAP.md): fases pendientes.
- [`docs/TESTING.md`](docs/TESTING.md): casos y validaciones recomendadas.

# Roadmap

## V1 — entregado en este ZIP

- [x] Registro/login JWT y BCrypt
- [x] Cuenta PEN por usuario
- [x] Transferencia atómica con validaciones
- [x] Ledger débito/crédito
- [x] Clave de idempotencia y bloqueo de cuentas
- [x] Frontend verde oscuro con login, dashboard y transferencias reales
- [x] Docker Compose + Flyway + documentación

## V1.1 — calidad y seguridad (pendiente)

- [ ] Pruebas de integración con Testcontainers/PostgreSQL y concurrencia real
- [ ] Test de idempotencia con reintentos simultáneos
- [ ] Tests de API con MockMvc y cobertura de autorizaciones
- [ ] Rate limiting, gestión de claves JWT, refresh seguro y auditoría
- [ ] Fondeo simulado mediante ledger equilibrado y controles administrativos
- [ ] Métricas reales para dashboard, filtros y paginación

## V2 — arquitectura de eventos (pendiente)

- [ ] Outbox transaccional + RabbitMQ
- [ ] Consumidores idempotentes, reintentos y dead-letter queue
- [ ] Notificaciones y monitor de operaciones
- [ ] OpenTelemetry, trazas y métricas
- [ ] CI/CD y despliegue demostrable

## V3 — ingeniería avanzada (pendiente)

- [ ] Reconciliación de saldos contra ledger
- [ ] Límites y políticas de riesgo
- [ ] Roles de soporte y operaciones
- [ ] Pruebas de carga y tolerancia a fallos

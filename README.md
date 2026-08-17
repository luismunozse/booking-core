# BookingCore

> Motor de reservas open source y agnóstico del dominio, construido con Java 21 y Spring Boot.

[![CI](https://github.com/luismunozse/booking-core/actions/workflows/ci.yml/badge.svg)](https://github.com/luismunozse/booking-core/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen.svg)](https://spring.io/projects/spring-boot)

## El problema

Reservar un recurso en una franja horaria es el mismo problema en un
consultorio, una cancha, una sala de reuniones o una flota de vehículos: existe
un recurso, existe una disponibilidad, y dos personas no pueden ocupar el mismo
lugar al mismo tiempo.

Ese núcleo se vuelve a implementar una y otra vez, casi siempre con los mismos
errores: reservas que se solapan cuando dos usuarios reservan a la vez,
disponibilidad calculada a mano, husos horarios mal modelados.

BookingCore resuelve ese núcleo una sola vez, sin saber nada del dominio de
quien lo usa. Para el motor, un consultorio, una cancha y un auto son lo mismo:
un `Resource`.

## Estado

**En construcción.** Todavía no hay API pública ni release publicada.

| Hito | Alcance | Estado |
| --- | --- | --- |
| M0 | Infraestructura: build, PostgreSQL, migraciones, health check, tests | ✅ Completo |
| M1 | `Resource`: modelo de dominio, CRUD, validación, contrato de errores | 🚧 En curso |
| M2 | `Availability`: modelado de tiempo y husos horarios | ⏳ |
| M3 | `Booking`: creación y detección de solapamientos | ⏳ |
| M4 | Concurrencia: bloqueos, constraints y tests multihilo | ⏳ |

## Conceptos

El vocabulario del motor es deliberadamente genérico:

| Concepto | Qué representa |
| --- | --- |
| `Resource` | Cualquier cosa reservable: una sala, una cancha, un consultorio, un vehículo, una persona |
| `Availability` | Cuándo un recurso puede reservarse |
| `Booking` | Una reserva concreta de un recurso en una franja horaria |
| `TimeSlot` | Una franja horaria |
| `Blackout` | Un período en el que un recurso no puede reservarse |

El core nunca conoce el dominio de quien lo integra. No hay ni habrá un
`HotelBooking` ni un `MedicalAppointment`.

## Cómo ejecutarlo

Requisitos: **Java 21** y un runtime de contenedores (Docker Desktop, OrbStack,
Podman o Colima).

```bash
# 1. Levantar PostgreSQL
docker compose up -d --wait

# 2. Arrancar la aplicación
./mvnw spring-boot:run

# 3. Verificar
curl -s localhost:8080/actuator/health
```

```json
{ "status": "UP", "components": { "db": { "status": "UP" } } }
```

> PostgreSQL se publica en el puerto **5433**, no en el 5432, para no chocar en
> silencio con una instalación nativa de PostgreSQL. Se puede cambiar con la
> variable `BOOKINGCORE_DB_PORT`, que la aplicación y `compose.yaml` leen por
> igual.

Para detenerlo:

```bash
docker compose down     # agregar -v para borrar también los datos
```

## Tests

```bash
./mvnw test
```

Los tests de integración levantan un **PostgreSQL real** con Testcontainers, no
una base en memoria. Es una decisión deliberada: H2 no reproduce el
comportamiento transaccional de PostgreSQL, y este proyecto se apoya en él para
resolver la concurrencia. Un test que pasa contra H2 y falla en producción no
sirve de nada.

## Stack

| | |
| --- | --- |
| Lenguaje | Java 21 |
| Framework | Spring Boot 4.1 |
| Build | Maven (con wrapper: no hace falta instalar Maven) |
| Base de datos | PostgreSQL 18 |
| Migraciones | Flyway |
| Tests | JUnit, AssertJ, Testcontainers |

## Decisiones de diseño

Las decisiones relevantes están documentadas como
[ADRs](docs/adr/README.md): el contexto que las forzó, las alternativas
consideradas y lo que cuestan.

- [0001](docs/adr/0001-flyway-gobierna-el-esquema.md) — Flyway gobierna el esquema, no el `ddl-auto` de Hibernate
- [0002](docs/adr/0002-modulo-maven-unico.md) — Un solo módulo Maven, no un build multi-módulo
- [0003](docs/adr/0003-usar-spring-boot-4.md) — Usar Spring Boot 4.x
- [0004](docs/adr/0004-desactivar-open-session-in-view.md) — Desactivar Open Session In View

## Fuera de alcance

Lo siguiente queda deliberadamente afuera, no pendiente:

- **Autenticación y autorización.** BookingCore es infraestructura para
  embeber, no un producto terminado. Quién puede reservar es una decisión de la
  aplicación que lo integra.
- **Pagos.** Reservar y cobrar son problemas distintos.
- **Frontend.** El motor expone una API.
- **Microservicios, colas de mensajes y caches distribuidas.** El problema no
  los necesita todavía, y agregarlos antes de necesitarlos solo agrega
  superficie de fallo.

## Licencia

[Apache License 2.0](LICENSE).

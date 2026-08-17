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
| M1 | `Resource`: modelo de dominio, CRUD, validación, contrato de errores | ✅ Completo |
| M2 | `Availability`: modelado de tiempo y husos horarios | 🚧 En curso |
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

## API

| Método | Ruta | Qué hace |
| --- | --- | --- |
| `POST` | `/api/v1/resources` | Crea un recurso. Devuelve `201` con la cabecera `Location` |
| `GET` | `/api/v1/resources` | Lista recursos paginados (`?page=0&size=20`) |
| `GET` | `/api/v1/resources/{id}` | Devuelve un recurso |
| `PATCH` | `/api/v1/resources/{id}` | Modifica los campos presentes en el cuerpo |
| `POST` | `/api/v1/resources/{id}/deactivate` | Desactiva el recurso. Devuelve `204` |
| `POST` | `/api/v1/resources/{id}/activate` | Reactiva el recurso. Devuelve `204` |

Los recursos no se borran: se desactivan. Borrarlos rompería la historia de las
reservas que los referencian.

Los errores siguen [RFC 7807](https://www.rfc-editor.org/rfc/rfc7807), con el
media type `application/problem+json`:

```json
{
  "type": "urn:bookingcore:problem:validation-failed",
  "title": "Solicitud inválida",
  "status": 400,
  "detail": "Uno o más campos no cumplen las restricciones",
  "instance": "/api/v1/resources",
  "errors": {
    "name": "el nombre es obligatorio",
    "type": "el tipo es obligatorio",
    "capacity": "la capacidad debe ser al menos 1"
  }
}
```

El campo `type` es un identificador estable: los clientes pueden ramificar sobre
él sin parsear mensajes de texto.

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

## Cómo está construido

La carpeta [docs/aprendizaje](docs/aprendizaje/README.md) explica los conceptos
detrás del código: el recorrido de una petición, cómo funcionan JPA e Hibernate,
qué resuelve cada pieza de infraestructura y por qué el código tiene la forma que
tiene. Está escrita para quien conoce Java pero no viene de trabajar con Spring
Boot.

## Decisiones de diseño

Las decisiones relevantes están documentadas como
[ADRs](docs/adr/README.md): el contexto que las forzó, las alternativas
consideradas y lo que cuestan.

- [0001](docs/adr/0001-flyway-gobierna-el-esquema.md) — Flyway gobierna el esquema, no el `ddl-auto` de Hibernate
- [0002](docs/adr/0002-modulo-maven-unico.md) — Un solo módulo Maven, no un build multi-módulo
- [0003](docs/adr/0003-usar-spring-boot-4.md) — Usar Spring Boot 4.x
- [0004](docs/adr/0004-desactivar-open-session-in-view.md) — Desactivar Open Session In View
- [0005](docs/adr/0005-prefijar-las-tablas.md) — Prefijar las tablas con `bookingcore_`
- [0006](docs/adr/0006-identidad-con-uuid-v7.md) — Identificar las entidades con UUID v7
- [0007](docs/adr/0007-type-como-etiqueta-opaca.md) — `type` es una etiqueta opaca, no un catálogo
- [0008](docs/adr/0008-contrato-de-errores-rfc-7807.md) — Contrato de errores con Problem Details (RFC 7807)

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

# 3. Usar Spring Boot 4.x

- **Estado:** Aceptado
- **Fecha:** 2026-08-17

## Contexto

El proyecto se definió originalmente contra "Spring Boot 3.x estable". Para
cuando se generó el esqueleto, `start.spring.io` ya no ofrecía ninguna versión
3.x: solamente 4.1.0 (por defecto), 4.0.7 y snapshots. Spring Initializr
retira las líneas de release cuando salen del soporte open source, así que
arrancar en 3.x habría significado empezar un proyecto nuevo sobre una línea
que ya no recibe actualizaciones gratuitas.

Spring Boot 4 trae Spring Framework 7 y Jakarta EE 11.

El argumento a favor de quedarse en 3.x era el volumen de tutoriales y
respuestas de Stack Overflow escritos para esa línea. Ese argumento es más
débil de lo que parece acá: las partes de este proyecto con valor formativo
real —transacciones, niveles de aislamiento, estrategias de bloqueo,
constraints de exclusión, los tests de concurrencia— son asuntos de Hibernate y
PostgreSQL, y no cambian entre una línea y la otra.

## Decisión

Usar Spring Boot 4.1.0 sobre Java 21.

## Consecuencias

Menos ejemplos de terceros aplican tal cual. Las diferencias encontradas al
construir el esqueleto quedan registradas acá para no volver a descubrirlas:

| Aspecto | Spring Boot 3.x | Spring Boot 4.x |
| --- | --- | --- |
| Starter web | `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| Flyway | agregar `flyway-core` a mano | `spring-boot-starter-flyway` |
| Soporte de test | un único `spring-boot-starter-test` | dividido por slice: `-webmvc-test`, `-data-jpa-test`, `-flyway-test`, `-actuator-test` |
| Jackson | `com.fasterxml.jackson` 2.x | `tools.jackson` 3.x |
| `TestRestTemplate` | autoregistrado, en `org.springframework.boot.test.web.client` | en `org.springframework.boot.resttestclient`, opt-in, y necesita `spring-boot-restclient` en el classpath |
| Cliente HTTP de test | `TestRestTemplate` / `WebTestClient` | `RestTestClient` (Spring Framework 7) |
| `@LocalServerPort` | — | `org.springframework.boot.test.web.server.LocalServerPort` |

Como `spring-boot-restclient` no lo arrastra el starter web, los tests de
integración usan `RestTestClient.bindToServer()` en lugar de
`TestRestTemplate`. Así se evita agregar una dependencia solo para alcanzar una
API heredada, y de todos modos la petición pasa por un socket real.

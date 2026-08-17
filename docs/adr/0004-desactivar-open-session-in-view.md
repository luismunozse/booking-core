# 4. Desactivar Open Session In View

- **Estado:** Aceptado
- **Fecha:** 2026-08-17

## Contexto

Spring Boot activa `spring.jpa.open-in-view` por defecto y registra una
advertencia al arrancar. La opción mantiene abierto el contexto de persistencia
de JPA durante toda la petición HTTP, y no solamente mientras dura la
transacción declarada en la capa de servicio.

La comodidad es que una asociación lazy todavía puede resolverse mientras se
serializa la respuesta. El costo es que un getter invocado desde la capa web
lanza una consulta a la base de datos en silencio, fuera de cualquier
transacción que el servicio haya declarado. Las consecuencias:

- Las consultas se disparan desde una capa que no tiene intención
  transaccional, de a una fila por vez. Es la forma habitual en que un problema
  N+1 llega a producción sin que nadie lo note.
- Las conexiones a la base quedan retenidas durante toda la petición, incluida
  la serialización de la respuesta, lo que reduce el rendimiento del pool bajo
  carga.
- Desaparece el límite de "¿qué datos cargó realmente este caso de uso?", algo
  que importa especialmente en una librería que otras aplicaciones embeben.

## Decisión

Configurar `spring.jpa.open-in-view: false`.

## Consecuencias

- Acceder a una asociación lazy no cargada fuera de una transacción ahora lanza
  `LazyInitializationException`. Ese es el comportamiento deseado: falla fuerte
  en desarrollo en lugar de degradarse en silencio en producción.
- Cada caso de uso debe declarar qué carga: mediante un fetch join explícito,
  un entity graph, o devolviendo una proyección DTO. Eso es la disciplina
  buscada, no un workaround.
- Los límites transaccionales se vuelven significativos y revisables, algo de
  lo que depende el trabajo de concurrencia más adelante en este proyecto.

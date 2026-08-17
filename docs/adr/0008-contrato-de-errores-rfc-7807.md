# 8. Contrato de errores con Problem Details (RFC 7807)

- **Estado:** Aceptado
- **Fecha:** 2026-08-17

## Contexto

Una API necesita una forma consistente de comunicar errores. Para una librería
que otras aplicaciones integran, ese formato **es parte del contrato público**:
si cambia, rompe a quien la consume, igual que si cambiara el cuerpo de una
respuesta exitosa.

Spring Boot trae un formato propio por defecto (`timestamp`, `status`, `error`,
`path`, `trace`). Sirve para depurar, pero no es un estándar: su forma ha
cambiado entre versiones de Spring Boot, y ata el contrato de BookingCore a
decisiones que no toma este proyecto.

La alternativa habitual es inventar un DTO de error propio. Funciona, pero
obliga a documentar y mantener un formato que ya existe estandarizado.

## Decisión

Usar **RFC 7807 Problem Details**, activando `spring.mvc.problemdetails.enabled`
(viene en `false`).

Tres precisiones sobre cómo se usa:

1. El campo `type` lleva un **URN** (`urn:bookingcore:problem:resource-not-found`)
   y no una URL. El RFC permite que no sea desreferenciable, y BookingCore no es
   dueño de ningún dominio donde alojar documentación. Un URN identifica la
   clase de problema de forma estable sin prometer una página que no existe.
2. Los errores de validación agregan una propiedad de extensión `errors` con el
   detalle campo por campo. El RFC habilita explícitamente estas extensiones.
   La respuesta por defecto solo dice que la petición es inválida, sin indicar
   qué campo, que es justo lo que necesita quien llama.
3. Las excepciones de dominio extienden `ErrorResponseException`, así que Spring
   ya sabe renderizarlas sin un handler dedicado por cada una.

## Consecuencias

- Las respuestas de error usan el media type `application/problem+json`, que los
  clientes pueden interpretar con librerías existentes en cualquier lenguaje.
- El campo `type` da a los clientes algo estable contra qué ramificar, en lugar
  de tener que parsear mensajes en castellano que pueden cambiar.
- Los mensajes de error del dominio y de las validaciones están en español, con
  lo cual quedan atados al idioma del proyecto. Internacionalizarlos requeriría
  `MessageSource`, que no se necesita todavía.
- Agregar una clase de error nueva significa agregar un URN nuevo. Esa lista es,
  en los hechos, parte de la documentación de la API.

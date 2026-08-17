# 7. `type` es una etiqueta opaca, no un catálogo

- **Estado:** Aceptado
- **Fecha:** 2026-08-17

## Contexto

`Resource` necesita alguna forma de categorización: el integrador querrá
distinguir canchas de consultorios, o salas de vehículos.

La opción intuitiva es un enum en el core:

```java
enum ResourceType { ROOM, COURT, VEHICLE, PROFESSIONAL }
```

Es la trampa más común en las librerías que intentan ser genéricas. Cada
vertical nuevo obliga a modificar el core y publicar una versión: un
consultorio odontológico que reserva sillones necesitaría un release de
BookingCore para poder usarlo. Es exactamente lo contrario de ser agnóstico del
dominio.

La alternativa seria era una tabla `bookingcore_resource_type` que el
integrador da de alta, con validación por clave foránea. Aísla igual de bien y
además evita erratas.

## Decisión

`type` es un `varchar(100)` obligatorio y no vacío. Nada más.

El argumento decisivo es que **el motor nunca ramifica según `type`**. No hay
ni habrá un `if (type == ...)` ni un `switch` sobre su valor. Si el core nunca
lee ese campo para decidir nada, entonces `type` no es una regla de negocio del
motor: es una etiqueta para el integrador. Y validar una etiqueta opaca
significaría que el core opina sobre un dominio que declaró no conocer.

La tabla de tipos se descartó por peso: suma una tabla, un repositorio,
endpoints de alta y baja, y un paso de configuración obligatorio antes de poder
crear el primer recurso.

## Consecuencias

- No hay validación: `"cancha"` y `"Cancha"` pueden convivir, y las consultas
  por tipo dependen de que el integrador sea consistente. Es su
  responsabilidad, igual que elegir el vocabulario.
- El motor no puede ofrecer "listá los tipos disponibles" sin un `SELECT
  DISTINCT`.
- Migrar más adelante a una tabla de tipos es una migración con backfill, no un
  rediseño: los valores existentes se convierten en filas.
- Esta decisión fija una regla que vale para todo el proyecto: si alguna vez
  aparece lógica del motor que dependa del valor de `type`, ese es el síntoma
  de que se filtró conocimiento de dominio y hay que revisar el diseño.

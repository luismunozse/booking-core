# 5. Prefijar las tablas con `bookingcore_`

- **Estado:** Aceptado
- **Fecha:** 2026-08-17

## Contexto

BookingCore está pensado para dos formas de despliegue: como servidor
independiente, y como librería embebida dentro de otra aplicación Spring Boot.

En el segundo caso sus tablas conviven con las del integrador en la misma base
de datos. Y los nombres que pide el dominio —`resource`, `booking`,
`availability`, `blackout`— son de los más genéricos que existen: la
probabilidad de que el integrador ya tenga una tabla `resource` es alta.

Una colisión de nombres en ese escenario no es un detalle estético: impide
instalar la librería.

## Decisión

Todas las tablas del motor llevan el prefijo `bookingcore_`. La primera es
`bookingcore_resource`.

Se consideró usar un esquema dedicado (`bookingcore.resource`), que aísla mejor
y permite otorgar permisos por separado. Se descartó porque complica los
despliegues multi-tenant, obliga a configurar `default_schema` en Hibernate y
`schemas` en Flyway, y algunas herramientas de migración y backup lo manejan
peor. El prefijo consigue lo esencial con menos partes móviles.

Es la convención establecida para librerías Java embebibles: Quartz usa
`QRTZ_`, Spring Batch usa `BATCH_` y Flowable usa `ACT_`.

## Consecuencias

- Los nombres son más largos y algo redundantes cuando el motor corre solo, con
  su propia base de datos.
- El integrador distingue de un vistazo qué tablas son del motor y cuáles
  suyas, lo que hace obvio qué puede tocar y qué no.
- Renombrar tablas después de publicar una versión sería un cambio
  incompatible, con migración de datos incluida. Por eso se decide ahora, con
  el esquema todavía vacío.

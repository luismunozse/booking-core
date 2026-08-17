# 1. Flyway gobierna el esquema, no el `ddl-auto` de Hibernate

- **Estado:** Aceptado
- **Fecha:** 2026-08-17

## Contexto

Hibernate puede derivar el esquema de la base de datos a partir de las
entidades mapeadas, mediante `spring.jpa.hibernate.ddl-auto`. Es cómodo durante
los primeros días de un proyecto y perjudicial después:

- El esquema pasa a ser un efecto secundario del código Java. No hay artefacto
  que revisar, no hay historial, y no hay forma de responder "¿qué cambió en la
  base de datos en la release que rompió producción?".
- `update` nunca elimina ni modifica nada que considere riesgoso, así que los
  entornos se van separando en silencio. El esquema de producción termina
  siendo el residuo acumulado de todas las versiones que alguna vez corrieron
  ahí.
- No puede expresar lo que este proyecto realmente va a necesitar. Impedir
  reservas superpuestas probablemente requiera DDL específico de PostgreSQL
  (constraints de exclusión, extensiones, índices parciales) que Hibernate no
  genera.

BookingCore almacena reservas. Esos datos no son descartables, y el proyecto
está pensado para embeberse en sistemas de otras personas, así que su esquema
es parte de su contrato público.

## Decisión

Flyway gobierna el esquema. Cada cambio es una migración SQL versionada en
`src/main/resources/db/migration`.

Hibernate queda configurado en `ddl-auto: validate`. Puede verificar que las
entidades mapeadas coincidan con el esquema, y nunca puede modificarlo. Una
discrepancia falla al arrancar, no en la primera consulta.

Se consideró Liquibase. Su abstracción sobre el DDL rinde cuando un mismo
changeset debe apuntar a varios motores de base de datos; BookingCore apunta a
PostgreSQL, y este proyecto específicamente *quiere* acceso directo al DDL de
PostgreSQL. Los archivos `.sql` planos de Flyway son la opción más simple.

## Consecuencias

- Los cambios de esquema se escriben a mano en SQL. Es más lento que dejar que
  Hibernate adivine, y ese es exactamente el punto: cada cambio se revisa como
  código.
- El esquema es reproducible. Una base limpia más el historial de migraciones
  produce exactamente el esquema de cualquier commit dado.
- `validate` convierte la divergencia entre entidades y esquema en un fallo de
  arranque, que es el momento más barato para descubrirla.
- Las migraciones son append-only una vez aplicadas en algún entorno. Editar
  una migración ya aplicada cambia su checksum y Flyway se niega a arrancar con
  `Migration checksum mismatch`. En desarrollo se resuelve recreando la base;
  si la migración ya se publicó, se resuelve con `flyway repair` o con una
  migración nueva, nunca editando la vieja.

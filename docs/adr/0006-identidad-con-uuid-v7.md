# 6. Identificar las entidades con UUID v7

- **Estado:** Aceptado
- **Fecha:** 2026-08-17

## Contexto

Cada entidad necesita una clave primaria. Las opciones razonables y lo que
cuesta cada una:

| | `Long` IDENTITY | `Long` SEQUENCE | `UUID` v4 | `UUID` v7 |
| --- | --- | --- | --- | --- |
| Tamaño | 8 bytes | 8 bytes | 16 bytes | 16 bytes |
| Localidad en el índice | óptima | óptima | mala | casi óptima |
| Permite batch inserts | no | sí | sí | sí |
| Generable antes del INSERT | no | parcial | sí | sí |
| Enumerable desde afuera | sí | sí | no | no |

Dos puntos que suelen pasarse por alto:

- `GenerationType.IDENTITY` impide el batching de inserts, porque Hibernate
  necesita recuperar la clave que genera la base para cada fila y no puede
  enviarlas agrupadas. Quien elija `Long` debería usar `SEQUENCE` con
  optimizador *pooled*.
- Un `UUID` v4 es aleatorio, así que cada insert cae en una página al azar del
  índice B-tree, provocando divisiones de página y peor aprovechamiento de la
  caché. La versión 7 lleva un prefijo de timestamp, con lo cual los inserts
  caen al borde derecho del índice como lo haría una secuencia.

## Decisión

`UUID` versión 7, generado por Hibernate mediante
`@UuidGenerator(style = Style.VERSION_7)`.

## Consecuencias

- La clave ocupa 16 bytes en lugar de 8. A la escala de este proyecto la
  diferencia es irrelevante.
- Los identificadores no son enumerables desde afuera, lo cual evita filtrar
  por la API cuántos recursos o reservas existen.
- Un identificador generable por el cliente es la base natural de la
  idempotencia que va a hacer falta al resolver la concurrencia: "creá la
  reserva con id X" es idempotente por construcción, porque reintentar no
  duplica. Con identificadores generados por la base haría falta un mecanismo
  aparte.
- No hace falta ninguna dependencia ni código propio: Hibernate 7.4 trae el
  generador de v7 incorporado.
- Hibernate asigna el id recién al persistir, así que una entidad todavía no
  guardada tiene `id == null`. De ahí la forma de `equals` y `hashCode` en las
  entidades: igualdad solo por id, y hash constante para que no cambie cuando
  el id se asigna.

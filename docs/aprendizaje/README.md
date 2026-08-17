# Cómo está construido BookingCore

Esta carpeta explica **los conceptos** que hay detrás del código, no las
decisiones. Las decisiones viven en [docs/adr](../adr/README.md) y responden
"¿por qué elegimos esto y no aquello?". Estos documentos responden algo
anterior: "¿qué es esto y cómo funciona?".

Están escritos para alguien que sabe Java pero no viene de trabajar con Spring
Boot, JPA o infraestructura de contenedores. Todos los ejemplos son código real
de este repositorio, no ejemplos inventados.

## Orden de lectura

Conviene leerlos en orden: cada uno se apoya en el anterior.

| | Documento | De qué trata |
| --- | --- | --- |
| 1 | [Cómo viaja una petición](01-como-viaja-una-peticion.md) | El recorrido completo desde el `curl` hasta el `INSERT`, y qué hace cada capa |
| 2 | [JPA e Hibernate](02-jpa-e-hibernate.md) | Entidades, transacciones, el contexto de persistencia y el famoso `flush` |
| 3 | [La infraestructura](03-la-infraestructura.md) | Maven, Docker, Flyway, Testcontainers y CI: qué resuelve cada pieza |
| 4 | [Las decisiones de diseño](04-decisiones-de-diseno.md) | Por qué el código tiene la forma que tiene |

## Cómo usar esto

Si al leer algo queda una duda, conviene anotarla en el momento. Casi siempre
la duda no es sobre la línea de código que se está mirando, sino sobre un
concepto de dos capas más abajo que se dio por sabido.

Las tres preguntas que más rinden frente a cualquier parte del proyecto:

1. **¿Quién ejecuta este código?** ¿Lo llamo yo, o lo llama el framework?
2. **¿En qué momento se ejecuta?** ¿Al arrancar, en cada petición, al confirmar
   una transacción?
3. **¿Qué pasaría si no estuviera?** Si la respuesta es "nada", probablemente
   sobra.

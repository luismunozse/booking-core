# 2. Un solo módulo Maven, no un build multi-módulo

- **Estado:** Aceptado
- **Fecha:** 2026-08-17

## Contexto

BookingCore se describe como un monolito modular. El reflejo habitual es
expresar esa modularidad como módulos Maven separados desde el primer día, por
ejemplo `bookingcore-domain`, `bookingcore-application` y
`bookingcore-infrastructure`.

Un módulo Maven separado aporta exactamente dos cosas: un artefacto publicable
de forma independiente, y una dirección de dependencias que el compilador puede
hacer cumplir. Hoy el proyecto no publica nada y no tiene código de dominio
cuyas dependencias podrían violarse.

El costo, en cambio, es inmediato: varios POM que mantener sincronizados, un
build más lento, un IDE más pesado, y una decisión de ubicación forzada en cada
archivo nuevo antes siquiera de entender el dominio.

## Decisión

Un único módulo Maven. La modularidad se expresa con **package-by-feature**
dentro de un mismo árbol de fuentes:

```
io.github.luismunozse.bookingcore
├── resource/
├── booking/
├── availability/
└── shared/
```

y no con `controller/`, `service/`, `repository/`.

La razón es la posibilidad de hacer cumplir los límites, no el gusto. Con
package-by-feature un repositorio puede declararse package-private, y entonces
el compilador vuelve imposible que otra feature acceda a los datos de esta. Con
package-by-layer todo tipo debe ser `public`, y la "modularidad" degrada a un
acuerdo verbal.

## Consecuencias

- Los límites los hacen cumplir los modificadores de acceso de Java en lugar
  del build. Es una garantía más débil que la de un módulo, pero no cuesta nada
  y cubre el caso que acá importa.
- Si más adelante se quiere una garantía más estricta (por ejemplo "el dominio
  no debe importar Spring"), un test con ArchUnit la consigue por mucho menos
  que una división en módulos.
- Dividir en módulos sigue siendo un refactor mecánico, y el momento de hacerlo
  es cuando haya algo que publicar por separado, muy probablemente
  `bookingcore-spring-boot-starter`.

# Architecture Decision Records

Cada archivo registra una decisión: el contexto que la forzó, las opciones que
se consideraron, qué se eligió y qué cuesta esa elección.

Están escritos para la próxima persona que pregunte "¿por qué está hecho así?",
incluidos los futuros mantenedores de este proyecto. Un ADR nunca se edita para
cambiar una decisión: se lo reemplaza por uno nuevo que lo supersede.

| ADR | Decisión | Estado |
| --- | --- | --- |
| [0001](0001-flyway-gobierna-el-esquema.md) | Flyway gobierna el esquema, no el `ddl-auto` de Hibernate | Aceptado |
| [0002](0002-modulo-maven-unico.md) | Un solo módulo Maven, no un build multi-módulo | Aceptado |
| [0003](0003-usar-spring-boot-4.md) | Usar Spring Boot 4.x | Aceptado |
| [0004](0004-desactivar-open-session-in-view.md) | Desactivar Open Session In View | Aceptado |

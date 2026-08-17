# 2. JPA e Hibernate

De acá salieron casi todas las decisiones raras del proyecto y también el único
bug real que tuvimos. Vale la pena entenderlo bien.

Antes que nada, dos nombres que se confunden:

- **JPA** es una especificación: un conjunto de interfaces y anotaciones
  (`@Entity`, `@Id`, `EntityManager`). No hace nada por sí sola.
- **Hibernate** es la implementación que efectivamente hace el trabajo. Es la
  que Spring Boot trae por defecto.

En este proyecto se usan anotaciones de JPA (`jakarta.persistence.*`) y algunas
propias de Hibernate (`org.hibernate.annotations.*`, como `@UuidGenerator`).

---

## 1. Qué problema resuelve

Una base de datos relacional guarda filas en tablas. Java maneja objetos con
referencias. Convertir de uno a otro a mano se ve así:

```java
String sql = "INSERT INTO bookingcore_resource (id, name, type, capacity, active, ...) VALUES (?, ?, ?, ?, ?, ...)";
try (PreparedStatement ps = connection.prepareStatement(sql)) {
    ps.setObject(1, resource.getId());
    ps.setString(2, resource.getName());
    // ... una línea por columna, en el orden correcto
}
```

Funciona, pero cada columna nueva obliga a tocar cada consulta, y el compilador
no ayuda: un orden equivocado se descubre en ejecución.

Un ORM (*Object-Relational Mapper*) automatiza esa traducción. Se declara una
vez cómo se corresponden clase y tabla, y a partir de ahí se trabaja con
objetos.

Lo importante: **el ORM no hace que la base desaparezca.** Sigue habiendo SQL,
transacciones y bloqueos. El ORM los oculta, y esconderlos sin entenderlos es la
fuente de la mayoría de los problemas de rendimiento en aplicaciones Java.

---

## 2. La entidad

Una entidad es una clase cuyas instancias se corresponden con filas de una
tabla. En
[`Resource`](../../src/main/java/io/github/luismunozse/bookingcore/resource/Resource.java):

```java
@Entity
@Table(name = "bookingcore_resource")
public class Resource {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;
}
```

- `@Entity` le dice a Hibernate que gestione esta clase.
- `@Table` fija el nombre de la tabla; sin ella usaría el nombre de la clase.
- `@Id` marca la clave primaria. Toda entidad necesita una.
- `@Column` describe la columna. Con `ddl-auto: validate`, Hibernate **verifica
  al arrancar** que la tabla real coincida y falla si no.

Hay un constructor que parece innecesario:

```java
protected Resource() {
}
```

Hibernate necesita crear instancias por reflexión al leer filas: primero
construye el objeto vacío y después rellena los campos. Para eso requiere un
constructor sin argumentos. Es `protected` y no `public` para que nadie lo
confunda con una forma legítima de crear un recurso.

---

## 3. El contexto de persistencia

Este es **el** concepto central, y el que más se da por sabido.

Cuando se abre una transacción, Hibernate abre también un cuaderno de trabajo:
el *contexto de persistencia*. Ahí anota cada entidad que carga o que se le
pide guardar, junto con una copia de cómo estaba en ese momento.

```
Transacción abierta
┌─────────────────────────────────────────────────┐
│ Contexto de persistencia                        │
│                                                 │
│  id=01a0…3c03  →  Resource(name="Sala A", …)    │
│                   copia original: "Sala A"      │
└─────────────────────────────────────────────────┘
```

Ese cuaderno vive **mientras dura la transacción** y se descarta al terminar.
Tiene dos funciones que explican casi todo lo demás:

1. **Es una caché.** Si dentro de la misma transacción se pide dos veces la
   misma fila, la segunda vez la devuelve de memoria sin ir a la base. Además
   garantiza que sea *el mismo objeto* Java, no dos copias.
2. **Detecta cambios.** Como guardó una copia del estado original, puede
   comparar y saber exactamente qué cambió.

---

## 4. Los estados de una entidad

Una misma instancia pasa por estados distintos, y confundirlos es la causa de
varios errores frecuentes.

| Estado | Qué significa |
| --- | --- |
| **Transitoria** | Recién creada con `new`. Hibernate no la conoce. No tiene `id`. |
| **Gestionada** | Está en el cuaderno. Hibernate la vigila: cualquier cambio se va a persistir. |
| **Desasociada** | Estuvo gestionada, pero la transacción terminó. Es un objeto Java común; los cambios ya no se guardan. |
| **Eliminada** | Marcada para borrarse al sincronizar. |

```java
Resource r = new Resource("Sala A", "SALA", 4);   // transitoria: id == null
repository.save(r);                                // gestionada: ya tiene id
// ... termina la transacción ...
r.rename("Sala B");                                // desasociada: NO se guarda
```

Este ciclo explica la forma de `equals` y `hashCode` en la entidad: una entidad
transitoria todavía no tiene identidad, así que solo puede ser igual a sí misma.
Está desarrollado en [ADR 0006](../adr/0006-identidad-con-uuid-v7.md).

---

## 5. Dirty checking: por qué `update()` no llama a `save()`

En
[`ResourceService.update()`](../../src/main/java/io/github/luismunozse/bookingcore/resource/ResourceService.java):

```java
Resource resource = requireExisting(id);   // queda gestionada
resource.rename(request.name());           // se modifica el objeto
// ... y no hay ningún repository.save(resource)
```

Y sin embargo el cambio se guarda.

El motivo es el cuaderno. Al leer la entidad, Hibernate anotó que `name` valía
`"Sala A"`. Al sincronizar, compara el valor actual contra esa copia, ve que
ahora dice `"Sala Principal"`, y genera el `UPDATE` correspondiente. Eso se
llama *dirty checking*.

Dos consecuencias prácticas:

- Llamar a `save()` sobre una entidad gestionada no está mal, pero es
  redundante.
- **Modificar una entidad gestionada sin querer también se persiste.** Si dentro
  de una transacción se toca un objeto que vino de la base "solo para calcular
  algo", ese cambio termina en la base. No hay forma de deshacerlo salvo revertir
  la transacción entera.

---

## 6. El `flush`: cuándo se ejecuta el SQL

Acá está el punto que más cuesta y el que nos costó un bug.

**Modificar un objeto no ejecuta SQL.** Hibernate acumula el trabajo pendiente y
lo manda todo junto al sincronizar. Ese momento se llama *flush*, y ocurre:

- al confirmar la transacción (siempre);
- antes de una consulta que podría verse afectada por los cambios pendientes;
- cuando se pide explícitamente con `flush()` o `saveAndFlush()`.

### El bug que tuvimos

La entidad declara dos marcas de tiempo generadas por Hibernate:

```java
@CreationTimestamp
private Instant createdAt;

@UpdateTimestamp
private Instant updatedAt;
```

El servicio hacía esto:

```java
return ResourceResponse.from(repository.save(resource));   // ← incorrecto
```

Y la API respondía:

```json
{ "name": "Cancha 2", "createdAt": null, "updatedAt": null }
```

El motivo: `@CreationTimestamp` se aplica **durante el flush**, no al llamar a
`save()`. El DTO se armaba antes de que Hibernate hubiera tocado esos campos.
En la base el valor terminaba correcto —el flush ocurría después, al confirmar
la transacción—, pero **la respuesta ya había salido con nulos**.

El `PATCH` tenía la misma falla de forma más sutil: devolvía el `updatedAt`
*anterior*, porque el nuevo se generaba después de armar la respuesta.

```
respuesta del PATCH   -> updatedAt: 14:20:00.737980Z   ← viejo
valor real en la base -> updatedAt: 14:20:00.847867Z   ← nuevo
```

La corrección es forzar la sincronización antes de leer esos campos:

```java
repository.saveAndFlush(resource);   // en el alta
repository.flush();                  // en la modificación, antes de armar el DTO
```

La lección que deja: **si se necesita leer un valor que genera la base o el ORM,
hay que asegurarse de que la sincronización ya ocurrió.**

---

## 7. `@Transactional` y el proxy

```java
@Service
@Transactional(readOnly = true)
class ResourceService {

    @Transactional
    ResourceResponse create(...) { ... }
}
```

Spring no modifica el método. Crea un **proxy**: una clase generada en tiempo de
ejecución que tiene los mismos métodos y envuelve al objeto real.

```
controlador → proxy → [abre transacción] → servicio real → [confirma o revierte]
```

Si el método lanza una excepción no controlada, el proxy revierte todo. Por eso
un caso de uso que toca tres tablas es todo o nada sin escribir una sola línea
de manejo de transacciones.

Dos consecuencias que sorprenden:

- **Una llamada interna no pasa por el proxy.** Si un método público llama a
  otro método de la misma clase anotado con `@Transactional`, esa anotación se
  ignora, porque la llamada es directa sobre `this`.
- `readOnly = true` a nivel de clase declara que, por defecto, las operaciones
  no escriben. Le permite a Hibernate saltearse el dirty checking y le avisa a
  PostgreSQL. Los métodos que sí escriben lo declaran explícitamente, lo que
  además hace evidente al leer el código cuáles modifican estado.

---

## 8. Por qué un test necesitaba `entityManager.clear()`

En
[`ResourcePersistenceIntegrationTest`](../../src/test/java/io/github/luismunozse/bookingcore/resource/ResourcePersistenceIntegrationTest.java):

```java
Resource saved = repository.saveAndFlush(new Resource("Consultorio 4", "CONSULTORIO", 1));
entityManager.clear();
Resource found = repository.findById(saved.getId()).orElseThrow();
```

Sin ese `clear()`, `findById` encuentra la entidad en el cuaderno y devuelve
**el mismo objeto que ya estaba en memoria**, sin consultar la base. El test
pasaría aunque el mapeo entre clase y tabla estuviera roto, porque nunca se
lee nada de PostgreSQL.

`clear()` vacía el cuaderno y fuerza a que la lectura vaya realmente a la base.
Es la diferencia entre un test que prueba el mapeo y uno que prueba que Java
recuerda lo que acaba de escribir.

---

## 9. Carga perezosa y por qué apagamos Open Session In View

Cuando una entidad tiene relaciones, Hibernate no las carga por defecto: deja un
sustituto vacío y trae los datos recién cuando alguien los pide. Eso es *carga
perezosa*.

El problema es que solo funciona **mientras el cuaderno siga abierto**. Sobre
una entidad desasociada, pedir una relación no cargada lanza
`LazyInitializationException`.

Spring Boot activa por defecto una opción llamada *Open Session In View*, que
mantiene el cuaderno abierto durante toda la petición HTTP para que eso nunca
pase. Suena útil y es la razón de un problema clásico: un getter llamado
mientras se serializa la respuesta dispara consultas a la base, de a una por
elemento, fuera de toda transacción declarada. Es la forma habitual en que un
problema N+1 llega a producción sin que nadie lo note.

En este proyecto está desactivado ([ADR 0004](../adr/0004-desactivar-open-session-in-view.md)),
y por eso el servicio devuelve DTO y no entidades: los datos se arman mientras
la transacción sigue viva, y al controlador llega un objeto plano que ya no
depende de la base.

---

## 10. El repositorio

```java
interface ResourceRepository extends JpaRepository<Resource, UUID> {
}
```

Una interfaz vacía, sin implementación, y sin embargo tiene `save`, `findById`,
`findAll`, `deleteAll` y varios más.

Spring Data genera la implementación al arrancar: detecta la interfaz, deduce la
entidad y el tipo de la clave de los parámetros genéricos, y crea un objeto que
delega en el `EntityManager` de Hibernate.

También sabe traducir nombres de métodos. Si se agregara:

```java
List<Resource> findByTypeAndActiveTrue(String type);
```

Spring Data analiza el nombre y genera la consulta correspondiente, sin escribir
SQL. Para casos complejos existe `@Query`, donde el SQL o el JPQL se escribe a
mano.

---

## Lo que conviene retener

- **El cuaderno (contexto de persistencia) explica casi todo**: la caché, el
  dirty checking, la carga perezosa y los estados de la entidad.
- **Modificar un objeto no ejecuta SQL.** Hibernate acumula y sincroniza después.
  Cuándo ocurre esa sincronización importa, y no entenderlo produce bugs que no
  parecen tener sentido.
- **`@Transactional` funciona mediante un proxy.** De ahí sale la regla de que
  las llamadas internas no lo activan.
- **El ORM esconde la base, no la elimina.** Ante cualquier duda de rendimiento,
  el primer paso es mirar el SQL que se está generando.

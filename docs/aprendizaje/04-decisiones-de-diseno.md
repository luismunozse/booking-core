# 4. Las decisiones de diseño

Por qué el código tiene la forma que tiene. Cada sección responde una pregunta
que es razonable hacerse mirando el proyecto.

---

## ¿Por qué hay tantos archivos para dar de alta un recurso?

Crear un recurso involucra seis archivos:

```
CreateResourceRequest    lo que entra por HTTP
ResourceController       traduce HTTP ↔ Java
ResourceService          el caso de uso y la transacción
Resource                 el objeto de dominio y sus reglas
ResourceRepository       el acceso a datos
ResourceResponse         lo que sale por HTTP
```

Se podría hacer en uno solo, y para un CRUD de una tabla funcionaría. El costo
aparece cuando el proyecto crece: cada archivo tiene un motivo para cambiar, y
cuando están mezclados, un cambio de formato de la API obliga a tocar el mismo
archivo donde viven las reglas de negocio.

La regla práctica: **cada pieza debería tener una sola razón para cambiar.**

- El controlador cambia si cambia el contrato HTTP.
- El servicio cambia si cambia el caso de uso.
- La entidad cambia si cambian las reglas del dominio.

Dicho eso, esto es un equilibrio y no un dogma. Agregar capas "por las dudas"
también es un error: cada una tiene que estar ganándose el lugar.

---

## ¿Por qué no devolver la entidad directamente?

Sería más corto:

```java
@GetMapping("/{id}")
Resource findById(@PathVariable UUID id) { ... }   // no lo hacemos
```

Tres razones, en orden de importancia:

**1. El esquema se filtraría a la API.** El JSON de respuesta sería un reflejo
directo de las columnas. Agregar una columna interna la publicaría sin que nadie
lo decida; renombrar una rompería a todos los clientes. El contrato HTTP debe
poder evolucionar por separado del esquema.

**2. Con Open Session In View apagado, la entidad ya no sirve.** Cuando el
controlador la recibe, la transacción terminó y la entidad está desasociada.
Cualquier relación no cargada explotaría al serializarse. El DTO se arma
adentro de la transacción, con los datos ya resueltos.

**3. Se filtraría todo.** Una entidad de usuario incluiría el hash de la
contraseña salvo que alguien se acuerde de excluirlo. Con DTO, se publica lo que
se escribió explícitamente: la exposición es una decisión, no un descuido.

---

## ¿Por qué las carpetas están organizadas por funcionalidad?

La organización habitual en tutoriales es por capa:

```
controller/    ResourceController, BookingController, AvailabilityController
service/       ResourceService, BookingService, AvailabilityService
repository/    ResourceRepository, BookingRepository, AvailabilityRepository
```

Este proyecto usa el otro eje:

```
resource/      Resource, ResourceController, ResourceService, ResourceRepository
booking/
availability/
shared/
```

El motivo no es estético: **es lo único que permite hacer cumplir los límites**.

Mirando el repositorio:

```java
interface ResourceRepository extends JpaRepository<Resource, UUID> {
}
```

No dice `public`. Es *package-private*, así que solo las clases de
`…bookingcore.resource` pueden usarlo. Cuando exista `BookingService`, el
compilador le va a impedir tocar la tabla de recursos por atajo: va a tener que
pasar por el servicio.

Con la organización por capa eso es imposible, porque `controller` necesita ver
a `service` y `service` necesita ver a `repository`: todo tiene que ser
`public`, y la "modularidad" queda como un acuerdo verbal que se rompe la
primera vez que alguien tiene apuro.

Además, agrupar por funcionalidad hace que un cambio se concentre en una
carpeta, en vez de obligar a saltar entre tres.

---

## ¿Por qué la entidad valida en el constructor si ya está Bean Validation?

```java
public Resource(String name, String type, int capacity) {
    this.name = requireText(name, "name");
    this.type = requireText(type, "type");
    this.capacity = requireValidCapacity(capacity);
    this.active = true;
}
```

Parece duplicado respecto de `@NotBlank` en el DTO, pero son dos garantías
distintas:

| | Dónde vive | Qué garantiza |
| --- | --- | --- |
| Bean Validation | DTO, borde de la API | Que el JSON entrante sea razonable, con un mensaje útil |
| Constructor | Entidad | Que **no pueda existir** un `Resource` inválido |

La segunda es la fuerte. Si las reglas vivieran solo en el DTO, cualquier código
que no pase por el controlador —un test, una tarea programada, otro servicio
interno— podría crear un recurso con capacidad cero.

Un objeto que solo es válido si alguien se acordó de validarlo antes es un
objeto frágil. Un objeto que no puede construirse inválido no necesita que nadie
se acuerde.

Y por eso las reglas se escriben con Java común y no con anotaciones: el dominio
no debería depender de una librería de validación para proteger sus propias
invariantes.

---

## ¿Por qué la entidad no tiene setters?

```java
resource.setActive(false);   // no existe
resource.deactivate();       // esto sí
```

Con setters públicos, cualquier código puede dejar el objeto en un estado
inválido, y las reglas terminan repartidas por todos los servicios que tocan el
campo.

Con métodos de negocio, la regla vive en un solo lugar. Cuando aparezca "un
recurso desactivado no acepta reservas nuevas", `deactivate()` es el lugar obvio
donde ponerla, y no hay forma de saltearlo.

El nombre también comunica: `setActive(false)` describe una mutación;
`deactivate()` describe una operación del negocio. Por eso la API expone
`POST /resources/{id}/deactivate` y no un `PATCH {"active": false}`.

Esta diferencia tiene nombre: un modelo con datos pero sin comportamiento se
llama *modelo anémico*. Funciona, pero la lógica se dispersa en servicios y el
objeto de dominio termina siendo una estructura de datos con nombre elegante.

---

## ¿Por qué las mismas reglas están en la base de datos?

```sql
CONSTRAINT bookingcore_resource_capacity_positive CHECK (capacity >= 1)
```

La capacidad ya se valida en el DTO y en el constructor. La restricción en la
base es la tercera vez.

El motivo: **la aplicación no es el único camino a los datos.** Hay scripts, hay
migraciones, hay gente conectándose con `psql` a las tres de la mañana para
arreglar algo. Una regla que solo vive en Java es una sugerencia para todos los
demás.

Esto se llama *defensa en profundidad*: cada capa valida lo suyo asumiendo que
las otras podrían fallar o ser esquivadas. Hay un test que lo comprueba
insertando con SQL nativo, salteando la entidad a propósito, y verificando que
PostgreSQL rechace la fila.

---

## ¿Qué es un ADR y para qué sirve?

Un *Architecture Decision Record* es un archivo corto que registra una decisión:
el contexto que la forzó, las alternativas que se consideraron, qué se eligió y
qué cuesta esa elección. Están en [docs/adr](../adr/README.md).

Por qué vale la pena escribirlos:

**El código muestra qué se hizo; el ADR muestra qué se descartó.** Dentro de seis
meses, mirando `ddl-auto: validate`, no hay forma de saber si fue una decisión
pensada o algo que quedó copiado de un tutorial. El ADR 0001 lo aclara y explica
por qué `update` sería peor.

**Obliga a entender la decisión.** Si no se puede escribir media página
justificando algo, probablemente todavía no está entendido. Varias decisiones de
este proyecto cambiaron mientras se escribía el ADR correspondiente.

**Un ADR no se edita para cambiar de opinión.** Se escribe uno nuevo que lo
supersede. La historia de las decisiones también es información.

---

## ¿Por qué faltan cosas que parecen obvias?

Hay ausencias deliberadas en el proyecto:

| Ausencia | Motivo |
| --- | --- |
| Columna `version` y bloqueo optimista | La estrategia de concurrencia se decide en M4, después de comparar alternativas. Agregarla ahora sería decidir sin haber comparado |
| Índices además de la clave primaria | Todavía no existe ninguna consulta que los use. Un índice sin consulta solo hace los `INSERT` más lentos |
| Autenticación | BookingCore es infraestructura para embeber. Quién puede reservar lo decide la aplicación que lo integra |
| `DELETE` de recursos | Borrar un recurso con reservas rompe la integridad y la historia. Se desactiva |
| Filtros en el listado | No hay caso de uso todavía |

El principio se llama **YAGNI** (*You Aren't Gonna Need It*): no construir algo
hasta que haga falta de verdad. Cada pieza que existe hay que mantenerla,
testearla y migrarla, aunque no la use nadie.

Con una excepción importante, que también aparece en el proyecto: **cuando algo
es mucho más caro de agregar después, conviene decidirlo temprano.** Por eso sí
existen desde el principio:

- El prefijo `bookingcore_` en las tablas. Renombrarlas después de publicar una
  versión sería un cambio incompatible con migración de datos.
- Las columnas `created_at` y `updated_at`. Agregarlas más tarde obliga a
  inventar valores para las filas existentes.
- `NOT NULL` en las columnas. Relajar una restricción es una línea; agregarla
  después obliga a corregir los datos que ya violan la regla.

La pregunta útil no es "¿lo voy a necesitar?", sino **"¿cuánto me cuesta
agregarlo después?"**. Si es barato, se espera. Si es caro e irreversible, se
decide ahora.

---

## Lo que conviene retener

- **Las capas existen para aislar motivos de cambio**, no para cumplir un
  diagrama.
- **Los límites que el compilador hace cumplir son los únicos reales.** Un
  acuerdo de equipo se rompe; un `package-private` no.
- **Un objeto que no puede construirse inválido** vale más que uno que se valida
  por afuera.
- **YAGNI se aplica en tensión con la reversibilidad.** Lo barato de agregar
  después se pospone; lo caro e irreversible se decide temprano.

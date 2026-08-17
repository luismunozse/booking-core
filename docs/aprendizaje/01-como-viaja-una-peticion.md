# 1. Cómo viaja una petición

Este documento sigue una sola petición real de punta a punta:

```bash
curl -X POST http://localhost:8080/api/v1/resources \
  -H 'Content-Type: application/json' \
  -d '{"name":"Cancha 2","type":"CANCHA","capacity":1}'
```

## El recorrido completo

```
1. curl abre una conexión TCP al puerto 8080
        ↓
2. Tomcat            servidor web embebido dentro de la aplicación
        ↓
3. DispatcherServlet decide qué método Java corresponde a esta URL
        ↓
4. Jackson           convierte el JSON en un CreateResourceRequest
        ↓
5. Bean Validation   revisa las anotaciones @NotBlank, @Min
        ↓
6. ResourceController.create()      traduce entre HTTP y Java
        ↓
7. ResourceService.create()         abre la transacción, ejecuta el caso de uso
        ↓
8. new Resource(...)                el dominio protege sus invariantes
        ↓
9. ResourceRepository.saveAndFlush()
        ↓
10. Hibernate        convierte el objeto en un INSERT
        ↓
11. Driver JDBC      manda ese SQL por la red
        ↓
12. PostgreSQL       escribe la fila
```

Y la respuesta hace el camino inverso: la entidad se convierte en un
`ResourceResponse`, Jackson lo convierte en JSON y Tomcat lo escribe en la
conexión.

Vamos paso por paso, deteniéndonos en lo que suele resultar opaco.

---

## Antes de la petición: ¿quién crea los objetos?

Esta es la pregunta que conviene resolver primero, porque si no todo lo demás
parece magia.

Mirando [`ResourceController`](../../src/main/java/io/github/luismunozse/bookingcore/resource/ResourceController.java):

```java
@RestController
@RequestMapping("/api/v1/resources")
class ResourceController {

    private final ResourceService service;

    ResourceController(ResourceService service) {
        this.service = service;
    }
}
```

En ningún lugar del proyecto aparece `new ResourceController(...)`. Tampoco
`new ResourceService(...)`. ¿Quién los crea?

**El contenedor de Spring.** Al arrancar la aplicación, Spring recorre los
paquetes buscando clases marcadas con anotaciones como `@RestController`,
`@Service` o `@Repository`. De cada una crea **una sola instancia** y la guarda.
Esas instancias se llaman *beans*.

Cuando Spring necesita construir el `ResourceController` ve que su constructor
pide un `ResourceService`, busca ese bean entre los que ya creó, y se lo pasa.
Eso es **inyección de dependencias**: el objeto declara qué necesita y alguien
más se lo entrega.

Por qué importa, más allá de ahorrarse un `new`:

- El controlador no sabe cómo se construye un `ResourceService`. Si mañana el
  servicio necesita tres colaboradores más, el controlador no cambia.
- En un test se le puede pasar otra implementación sin tocar el código.
- Hay una sola instancia de cada uno, compartida por todas las peticiones. Esto
  tiene una consecuencia importante: **los beans no deben guardar estado de una
  petición en sus campos**, porque muchas peticiones los usan al mismo tiempo.
  Por eso `ResourceService` solo tiene un campo, y es final.

Todo esto ocurre **una vez, al arrancar**. Cuando llega la primera petición, los
objetos ya existen.

---

## Paso 2: Tomcat

Tomcat es un servidor web. Lo llamativo es que no está instalado en la máquina:
viene como una librería más dentro del proyecto, la trae
`spring-boot-starter-webmvc`, y la aplicación lo arranca sola.

Por eso `./mvnw spring-boot:run` levanta algo que escucha en el 8080 sin haber
configurado ningún servidor. Y por eso el resultado final es un único `.jar`
ejecutable, sin nada que instalar aparte.

Tomcat solo entiende de HTTP: abre conexiones, lee bytes, parsea cabeceras.
No sabe nada de recursos ni de reservas.

## Paso 3: DispatcherServlet

Es el recepcionista de Spring MVC. Recibe **todas** las peticiones y decide a
qué método enviar cada una.

Lo hace con la información de las anotaciones. Al arrancar, Spring armó una
tabla parecida a esta:

| Método HTTP | Ruta | Método Java |
| --- | --- | --- |
| POST | `/api/v1/resources` | `ResourceController.create` |
| GET | `/api/v1/resources/{id}` | `ResourceController.findById` |
| PATCH | `/api/v1/resources/{id}` | `ResourceController.update` |

`@RequestMapping("/api/v1/resources")` sobre la clase y `@PostMapping` sobre el
método son lo que llena esa tabla. No son documentación: son la configuración
del ruteo.

Si ninguna fila coincide, el DispatcherServlet responde 404 sin que se ejecute
código propio.

## Paso 4: Jackson convierte el JSON

El cuerpo de la petición llega como texto:

```json
{"name":"Cancha 2","type":"CANCHA","capacity":1}
```

La anotación `@RequestBody` le dice a Spring: "convertí ese texto en el objeto
que declara el parámetro". La conversión la hace Jackson, la librería de JSON.

Jackson mira el tipo destino —`CreateResourceRequest`, que es un `record`— y
empareja las claves del JSON con sus componentes. Una clave que no exista en el
record se ignora; un componente que no venga en el JSON queda en `null`.

Ese último detalle explica dos decisiones del proyecto:

- `capacity` es `Integer` y no `int` en
  [`UpdateResourceRequest`](../../src/main/java/io/github/luismunozse/bookingcore/resource/UpdateResourceRequest.java),
  porque hace falta distinguir "no lo mandaron" (`null`) de "mandaron cero".
  Un `int` no puede representar la ausencia.
- Los DTO son `record` y no clases: son datos inmutables sin comportamiento,
  que es exactamente para lo que existen los records.

## Paso 5: Bean Validation

La anotación `@Valid` en el parámetro dispara la validación:

```java
ResponseEntity<ResourceResponse> create(@Valid @RequestBody CreateResourceRequest request, ...)
```

Spring recorre las anotaciones del DTO (`@NotBlank`, `@Size`, `@Min`) y junta
todas las violaciones. Si hay al menos una, **lanza una excepción y el método
del controlador nunca se ejecuta**.

Esa excepción la atrapa
[`GlobalExceptionHandler`](../../src/main/java/io/github/luismunozse/bookingcore/shared/web/GlobalExceptionHandler.java),
que la convierte en una respuesta 400 con el detalle campo por campo.

Detalle importante: valida **todos** los campos y devuelve todos los errores
juntos, no se detiene en el primero. Quien consume la API arregla la llamada de
una vez y no de a un campo por intento.

## Paso 6: el controlador

```java
ResourceResponse created = service.create(request);
URI location = uriBuilder.path("/api/v1/resources/{id}").buildAndExpand(created.id()).toUri();
return ResponseEntity.created(location).body(created);
```

Tres líneas, y ninguna contiene una regla de negocio. Eso es a propósito: **el
controlador traduce entre el mundo HTTP y el mundo Java, nada más.**

Lo que sí le corresponde: elegir el código de estado (201), armar la cabecera
`Location`, decidir la forma del cuerpo.

Lo que no le corresponde: decidir si un recurso puede crearse, calcular valores
por defecto, hablar con la base de datos.

### ¿Por qué no meter todo acá?

Es la pregunta razonable cuando el servicio parece un intermediario que solo
reenvía llamadas. Tres motivos concretos:

1. **La lógica quedaría atada a HTTP.** Si mañana BookingCore se usa como
   librería embebida —que es el plan—, quien la integre quiere llamar a
   `service.create(...)` desde su propio código, sin levantar un servidor web.
2. **Las transacciones necesitan un lugar.** Un caso de uso que toca tres tablas
   debe ser todo o nada. Ese límite se marca en el servicio, no en el
   controlador.
3. **Los tests.** Probar una regla de negocio sin arrancar Tomcat ni serializar
   JSON es órdenes de magnitud más rápido.

## Paso 7: el servicio y la transacción

```java
@Service
@Transactional(readOnly = true)
class ResourceService {

    @Transactional
    ResourceResponse create(CreateResourceRequest request) { ... }
}
```

Acá pasa algo que conviene entender bien, y tiene su propio documento:
[JPA e Hibernate](02-jpa-e-hibernate.md).

La versión corta: cuando el controlador llama a `service.create(...)`, **no está
llamando directamente al objeto**. Spring envolvió el servicio en un *proxy*: un
objeto generado que tiene los mismos métodos, y que antes de delegar en el real
abre una transacción en la base de datos, y al terminar la confirma o la revierte
si hubo una excepción.

Eso explica una regla que sorprende: si un método de la clase llama a otro
método de la misma clase, la anotación `@Transactional` del segundo **no tiene
efecto**, porque esa llamada no pasa por el proxy.

## Paso 8: el dominio se defiende

```java
Resource resource = new Resource(request.name(), request.type(), request.capacityOrDefault());
```

Acá recién aparece el objeto de dominio, y su constructor vuelve a validar,
aunque Bean Validation ya lo hizo en el paso 5.

No es redundancia inútil: son dos garantías distintas.

- Bean Validation protege **el borde de la API**: rechaza JSON malformado con un
  mensaje útil.
- El constructor protege **el objeto**: hace imposible que exista un `Resource`
  inválido, venga de donde venga. De un test, de un job programado, de otro
  servicio interno.

Si las reglas vivieran solo en el DTO, cualquier código que no pase por el
controlador podría saltearlas.

## Pasos 9 a 12: hasta la base

El repositorio, Hibernate, JDBC y PostgreSQL son el tema del
[documento 2](02-jpa-e-hibernate.md).

---

## Lo que conviene retener

- **El framework llama al código, no al revés.** Casi nada de lo que se escribe
  se invoca a mano: se declara con anotaciones y Spring decide cuándo ejecutarlo.
  Esto se llama inversión de control, y es el cambio mental más grande al pasar
  de Java "a secas" a Spring.
- **Cada capa tiene un vocabulario propio.** El controlador habla de códigos de
  estado y cabeceras. El servicio habla de casos de uso y transacciones. El
  dominio habla de recursos y capacidades. Cuando el vocabulario se mezcla —un
  controlador con reglas de negocio, una entidad que sabe de JSON— el diseño se
  empieza a degradar.
- **Las anotaciones son configuración ejecutable**, no comentarios. `@Valid`
  ejecuta código. `@Transactional` abre una transacción. `@PostMapping` llena
  una tabla de ruteo.

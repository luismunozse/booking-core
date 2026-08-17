# 3. La infraestructura

Maven, Docker, Flyway, Testcontainers y CI parecen cinco herramientas sueltas.
En realidad todas atacan **el mismo problema**, cada una en una capa distinta:

> Que el proyecto se comporte igual en cualquier máquina, hoy y dentro de dos
> años.

Sin eso, "en mi máquina funciona" deja de ser un chiste y pasa a ser una forma
de trabajo.

---

## Maven y el wrapper

Maven es la herramienta de construcción: descarga dependencias, compila, ejecuta
los tests y empaqueta el `.jar`. Todo lo declara el
[`pom.xml`](../../pom.xml).

Lo interesante del proyecto es que **Maven no está instalado en la máquina**.
Hay dos archivos, `mvnw` y `mvnw.cmd`, que son el *Maven Wrapper*: un script que
descarga la versión exacta de Maven que el proyecto declara y la usa.

Por eso siempre se ejecuta `./mvnw test` y nunca `mvn test`. La diferencia:

- `mvn` usa la versión instalada en la máquina, que puede ser distinta en cada
  computadora del equipo.
- `./mvnw` usa la versión que dice
  [`.mvn/wrapper/maven-wrapper.properties`](../../.mvn/wrapper/maven-wrapper.properties),
  la misma para todos y también para el servidor de integración continua.

Es el primer eslabón de la reproducibilidad, y es gratis.

### Los starters

En el `pom.xml` las dependencias se llaman `spring-boot-starter-algo`. Un
starter no es una librería: es un paquete de librerías compatibles entre sí.

`spring-boot-starter-webmvc` trae Spring MVC, Tomcat embebido y Jackson, todos
en versiones que se sabe que funcionan juntas. Sin starters, habría que elegir a
mano la versión de cada una y resolver los conflictos.

---

## Docker: qué es realmente un contenedor

Un contenedor es un proceso aislado que se ejecuta con su propio sistema de
archivos, su propia red y sus propios procesos visibles. No es una máquina
virtual: comparte el núcleo del sistema operativo, así que arranca en
milisegundos.

Una **imagen** es la plantilla, de solo lectura (`postgres:18`). Un
**contenedor** es una instancia en ejecución de esa imagen.

### Por qué no instalar PostgreSQL directamente

- La versión queda atada a la máquina. Este proyecto usa PostgreSQL 18; si otra
  persona tiene la 15, se van a comportar distinto.
- Desinstalar y volver a empezar es incómodo. Con contenedores es
  `docker compose down -v`.
- El mismo `compose.yaml` sirve para cualquiera que clone el repositorio.

Esto no es hipotético: en esta máquina había un PostgreSQL 17 instalado con
Homebrew escuchando en el puerto 5432, y la aplicación se conectaba **a ese** en
lugar de al contenedor. El síntoma era desconcertante:

```
FATAL: role "bookingcore" does not exist
```

El rol existía… en el contenedor. La aplicación le estaba preguntando a otra
base. Por eso
[`compose.yaml`](../../compose.yaml) publica el 5433 y no el 5432.

### Volúmenes

Un contenedor es descartable: cuando se elimina, se pierde lo que escribió
adentro. Para que los datos sobrevivan se monta un *volumen*, que es
almacenamiento que vive fuera del contenedor.

Acá también hubo una trampa real. PostgreSQL 18 cambió dónde guarda sus datos:

```
PostgreSQL ≤ 17:  /var/lib/postgresql/data
PostgreSQL 18:    /var/lib/postgresql/18/docker
```

Montar la ruta vieja no da ningún error: el contenedor arranca, todo parece
funcionar, y los datos se pierden en cada reinicio en silencio.

---

## Docker Compose

Docker por sí solo maneja un contenedor por vez, con comandos largos. Compose
describe el conjunto en un archivo:

```yaml
services:
  postgres:
    image: postgres:18
    environment:
      POSTGRES_DB: bookingcore
    ports:
      - "${BOOKINGCORE_DB_PORT:-5433}:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U bookingcore -d bookingcore"]
```

Dos detalles que valen:

- `"5433:5432"` significa *puerto del host : puerto del contenedor*. Adentro
  PostgreSQL escucha en el 5432 de siempre; desde afuera se lo alcanza en el
  5433.
- El `healthcheck` permite que `docker compose up --wait` espere hasta que la
  base **acepte conexiones**, y no solamente hasta que el proceso exista. Sin
  eso, arrancar la aplicación inmediatamente después falla la mitad de las veces.

---

## Flyway: el esquema como código

El problema que resuelve: sin migraciones, la única definición del esquema es lo
que hay dentro de la base. No hay historia, no hay revisión de cambios, y las
bases de cada entorno se van separando sin que nadie lo note.

Flyway aplica archivos `.sql` numerados, en orden, y registra cuáles ya aplicó
en una tabla propia:

```
src/main/resources/db/migration/
├── V1__baseline.sql
└── V2__crear_tabla_resource.sql
```

```
 version |     description      | success
       1 | baseline             | t
       2 | crear tabla resource | t
```

Al arrancar, Flyway compara los archivos contra esa tabla y aplica lo que falte.
Una base vacía más el historial completo reproduce exactamente el esquema de
cualquier commit.

### El checksum, y por qué existe

Flyway guarda además un *checksum* de cada migración aplicada. En este proyecto
lo comprobamos sin querer: al traducir al español los comentarios de
`V1__baseline.sql`, la aplicación dejó de arrancar.

```
Validate failed: Migrations have failed validation
Migration checksum mismatch for migration version 1
```

No es un capricho. Si permitiera editar migraciones ya aplicadas, el archivo del
repositorio y el esquema real de producción podrían decir cosas distintas sin
que nadie se entere, que es exactamente el problema que Flyway existe para
evitar.

Cómo se resuelve según el caso:

| Situación | Solución |
| --- | --- |
| Desarrollo local, migración nunca publicada | Recrear la base (`docker compose down -v`) |
| Ya se aplicó en otro lado | `flyway repair`, que recalcula los checksums |
| Ya está en producción | Una migración **nueva**. Nunca editar la vieja |

### `ddl-auto: validate`

Hibernate puede generar el esquema a partir de las entidades. En este proyecto
tiene prohibido modificarlo: `validate` solo le permite **verificar al arrancar**
que la tabla real coincida con lo que las entidades declaran, y fallar si no.
Está desarrollado en [ADR 0001](../adr/0001-flyway-gobierna-el-esquema.md).

---

## Testcontainers

El dilema de siempre en los tests: una base en memoria como H2 es rápida, pero
no es PostgreSQL. Sus transacciones, sus tipos y sus restricciones se comportan
distinto.

Testcontainers levanta un PostgreSQL **real** en un contenedor durante los
tests, y lo destruye al terminar:

```java
@Bean
@ServiceConnection
PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
}
```

`@ServiceConnection` es la pieza cómoda: Spring Boot detecta el contenedor y
configura el `DataSource` solo, sin escribir URLs ni credenciales en ningún
lado.

Para este proyecto no es un lujo. El objetivo central —impedir reservas
superpuestas bajo concurrencia— depende del comportamiento transaccional
concreto de PostgreSQL. Un test que pasa contra H2 y falla en producción no
prueba nada.

La imagen está fijada en `postgres:18` a propósito. Con `latest`, el día que
salga PostgreSQL 19 el build cambiaría de comportamiento sin que nadie toque una
línea de código.

---

## Integración continua

[`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) le pide a GitHub que
ejecute la suite completa en cada push y en cada pull request, en una máquina
limpia:

```yaml
- uses: actions/setup-java@v5
  with:
    java-version: '21'
    distribution: temurin
- run: ./mvnw --batch-mode --no-transfer-progress verify
```

Los runners de GitHub incluyen Docker, así que Testcontainers funciona igual que
en local: los tests corren contra PostgreSQL de verdad.

Lo valioso no es el badge verde, sino que **la máquina de CI está vacía**. No
tiene el JDK correcto por casualidad, ni una base con datos viejos, ni una
variable de entorno que alguien puso hace seis meses. Si pasa ahí, el proyecto
es realmente reproducible.

Conviene recordar que un badge verde solo vale si los tests corrieron de verdad.
Por eso, después de configurar CI, verificamos en el log que efectivamente
apareciera `Tests run: 25` y no un salteo silencioso.

---

## Cómo encajan todas

```
┌─ CI (GitHub Actions) ────────────────────────────────┐
│  máquina limpia, en cada push                        │
│                                                      │
│  ./mvnw verify                                       │
│      │                                               │
│      ├── Maven: compila y resuelve dependencias      │
│      │                                               │
│      └── Tests                                       │
│            └── Testcontainers levanta PostgreSQL 18  │
│                  └── Flyway aplica V1, V2, …         │
│                        └── Hibernate valida el       │
│                            esquema contra las        │
│                            entidades                 │
└──────────────────────────────────────────────────────┘

En desarrollo local, lo mismo con compose.yaml en lugar de Testcontainers.
```

Cada capa verifica la anterior. Si Flyway y las entidades se desincronizan, la
aplicación no arranca. Si un test rompe algo, CI lo detiene antes del merge.

---

## Lo que conviene retener

- **Todas estas herramientas persiguen lo mismo:** que el resultado no dependa
  de la máquina donde se ejecuta.
- **Fijar versiones no es paranoia.** `postgres:latest` y `mvn` global son dos
  formas de dejar que el build cambie solo.
- **Los fallos más caros son los silenciosos.** El volumen mal montado y el
  puerto ocupado no daban ningún error: simplemente hacían algo distinto de lo
  esperado.
- **Reproducible significa desde cero.** No "funciona si además tenés instalado
  esto otro".

package io.github.luismunozse.bookingcore.resource;

import java.time.temporal.ChronoUnit;
import java.util.UUID;

import io.github.luismunozse.bookingcore.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ejercita la API de recursos sobre HTTP real, contra un PostgreSQL real.
 *
 * <p>Verifica el contrato tal como lo ve quien consume la librería: códigos de
 * estado, cabecera Location, forma del JSON y el formato de los errores.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ResourceApiIntegrationTest {

	@LocalServerPort
	private int port;

	@Autowired
	private ResourceRepository repository;

	private RestTestClient client;

	@BeforeEach
	void setUp() {
		client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();

		// Estos tests van por HTTP real, así que corren en otra transacción que
		// la del test: no hay rollback automático y hay que limpiar a mano.
		repository.deleteAll();
	}

	@Test
	void createsAResourceAndReturnsItsLocation() {
		client.post().uri("/api/v1/resources")
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
						{"name": "Cancha 2", "type": "CANCHA", "capacity": 1}
						""")
				.exchange()
				.expectStatus().isCreated()
				.expectHeader().exists("Location")
				.expectBody()
				.jsonPath("$.id").exists()
				.jsonPath("$.name").isEqualTo("Cancha 2")
				.jsonPath("$.type").isEqualTo("CANCHA")
				.jsonPath("$.capacity").isEqualTo(1)
				.jsonPath("$.active").isEqualTo(true);
	}

	@Test
	void defaultsCapacityToOneWhenOmitted() {
		client.post().uri("/api/v1/resources")
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
						{"name": "Consultorio 4", "type": "CONSULTORIO"}
						""")
				.exchange()
				.expectStatus().isCreated()
				.expectBody()
				.jsonPath("$.capacity").isEqualTo(1);
	}

	@Test
	void reportsEveryInvalidFieldAsProblemJson() {
		client.post().uri("/api/v1/resources")
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
						{"name": "   ", "type": "", "capacity": 0}
						""")
				.exchange()
				.expectStatus().isBadRequest()
				.expectBody()
				.jsonPath("$.title").isEqualTo("Solicitud inválida")
				.jsonPath("$.type").isEqualTo("urn:bookingcore:problem:validation-failed")
				.jsonPath("$.status").isEqualTo(400)
				// El detalle campo por campo es lo que necesita quien consume la
				// API para corregir la llamada.
				.jsonPath("$.errors.name").exists()
				.jsonPath("$.errors.type").exists()
				.jsonPath("$.errors.capacity").exists();
	}

	@Test
	void returnsProblemJsonForAnUnknownResource() {
		client.get().uri("/api/v1/resources/{id}", UUID.randomUUID())
				.exchange()
				.expectStatus().isNotFound()
				.expectBody()
				.jsonPath("$.title").isEqualTo("Recurso no encontrado")
				.jsonPath("$.type").isEqualTo("urn:bookingcore:problem:resource-not-found")
				.jsonPath("$.status").isEqualTo(404);
	}

	@Test
	void patchChangesOnlyTheFieldsPresent() {
		ResourceResponse created = create("Sala A", "SALA", 4);

		client.patch().uri("/api/v1/resources/{id}", created.id())
				.contentType(MediaType.APPLICATION_JSON)
				.body("""
						{"name": "Sala Principal"}
						""")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.name").isEqualTo("Sala Principal")
				// capacity no venía en el cuerpo, así que no se tocó.
				.jsonPath("$.capacity").isEqualTo(4);
	}

	@Test
	void deactivatesAResourceWithoutDeletingIt() {
		ResourceResponse created = create("Auto Toyota Corolla", "VEHICULO", 1);

		client.post().uri("/api/v1/resources/{id}/deactivate", created.id())
				.exchange()
				.expectStatus().isNoContent();

		client.get().uri("/api/v1/resources/{id}", created.id())
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.active").isEqualTo(false)
				.jsonPath("$.name").isEqualTo("Auto Toyota Corolla");
	}

	@Test
	void listsResourcesInPages() {
		create("Sala 1", "SALA", 1);
		create("Sala 2", "SALA", 1);
		create("Sala 3", "SALA", 1);

		client.get().uri("/api/v1/resources?page=0&size=2")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.content.length()").isEqualTo(2)
				.jsonPath("$.page").isEqualTo(0)
				.jsonPath("$.size").isEqualTo(2)
				.jsonPath("$.totalElements").isEqualTo(3)
				.jsonPath("$.totalPages").isEqualTo(2);
	}

	/**
	 * Las marcas de tiempo las genera Hibernate al sincronizar con la base, no
	 * al llamar a {@code save()}. Si el servicio arma la respuesta antes de esa
	 * sincronización, el alta devuelve nulos y el PATCH devuelve la marca vieja:
	 * la API miente sobre el estado real del recurso aunque la base quede bien.
	 */
	@Test
	void theResponsesReflectWhatIsActuallyStored() {
		ResourceResponse created = create("Sala A", "SALA", 4);

		assertThat(created.createdAt()).as("createdAt en la respuesta del alta").isNotNull();
		assertThat(created.updatedAt()).as("updatedAt en la respuesta del alta").isNotNull();

		ResourceResponse patched = client.patch().uri("/api/v1/resources/{id}", created.id())
				.contentType(MediaType.APPLICATION_JSON)
				.body(new UpdateResourceRequest("Sala Principal", null))
				.exchange()
				.expectStatus().isOk()
				.expectBody(ResourceResponse.class)
				.returnResult()
				.getResponseBody();

		assertThat(patched.updatedAt())
				.as("el PATCH debe devolver la marca nueva, no la anterior")
				.isAfter(created.updatedAt());

		ResourceResponse stored = client.get().uri("/api/v1/resources/{id}", created.id())
				.exchange()
				.expectStatus().isOk()
				.expectBody(ResourceResponse.class)
				.returnResult()
				.getResponseBody();

		assertThat(patched.updatedAt().truncatedTo(ChronoUnit.MILLIS))
				.as("la respuesta del PATCH debe coincidir con lo que quedó persistido")
				.isEqualTo(stored.updatedAt().truncatedTo(ChronoUnit.MILLIS));
	}

	private ResourceResponse create(String name, String type, int capacity) {
		return client.post().uri("/api/v1/resources")
				.contentType(MediaType.APPLICATION_JSON)
				.body(new CreateResourceRequest(name, type, capacity))
				.exchange()
				.expectStatus().isCreated()
				.expectBody(ResourceResponse.class)
				.returnResult()
				.getResponseBody();
	}

}

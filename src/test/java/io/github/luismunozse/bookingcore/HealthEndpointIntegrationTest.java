package io.github.luismunozse.bookingcore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Ejercita el stack completo sobre HTTP real: servidor web embebido, Actuator y
 * la conexión JDBC a PostgreSQL. Si este test pasa, el walking skeleton camina
 * de punta a punta.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthEndpointIntegrationTest {

	@LocalServerPort
	private int port;

	private RestTestClient client;

	@BeforeEach
	void bindToRunningServer() {
		// bindToServer() pasa por un socket real, así que esto también cubre el
		// servidor embebido y el binding del puerto, no solo el handler de MVC.
		client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
	}

	@Test
	void reportsUpIncludingTheDatabaseComponent() {
		client.get().uri("/actuator/health")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("UP")
				// El componente de base de datos es el punto de este test: un
				// estado general en verde no significa nada si Actuator no está
				// consultando PostgreSQL de verdad.
				.jsonPath("$.components.db.status").isEqualTo("UP");
	}

}

package io.github.luismunozse.bookingcore;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	/**
	 * Versión fijada a propósito: los tests deben correr contra la misma versión
	 * mayor de PostgreSQL que se usa en compose.yaml y en producción. Usar
	 * "latest" haría que el build cambie de comportamiento sin tocar una línea
	 * de código. Mantener sincronizado con compose.yaml.
	 */
	private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:18");

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(POSTGRES_IMAGE);
	}

}

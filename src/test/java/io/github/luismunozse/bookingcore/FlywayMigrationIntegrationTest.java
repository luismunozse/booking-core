package io.github.luismunozse.bookingcore;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que el pipeline de Flyway esté bien conectado contra una instancia
 * real de PostgreSQL: que se escanee la ubicación de las migraciones, que se
 * apliquen todas y que queden registradas en el historial.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FlywayMigrationIntegrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void appliesEveryMigrationInOrder() {
		List<String> appliedVersions = jdbcTemplate.queryForList(
				"SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank",
				String.class);

		assertThat(appliedVersions).containsExactly("1", "2");
	}

	@Test
	void containsOnlyTheExpectedTables() {
		List<String> tables = jdbcTemplate.queryForList("""
				SELECT table_name
				FROM information_schema.tables
				WHERE table_schema = 'public'
				ORDER BY table_name
				""", String.class);

		// Este test actúa como guardián de regresión: si alguien reintroduce
		// hibernate.ddl-auto=update, Hibernate empezaría a crear tablas por
		// detrás de Flyway y acá aparecerían tablas que nadie declaró.
		assertThat(tables).containsExactly("bookingcore_resource", "flyway_schema_history");
	}

}

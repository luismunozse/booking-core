package io.github.luismunozse.bookingcore.resource;

import java.util.UUID;

import io.github.luismunozse.bookingcore.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica el mapeo de {@link Resource} contra un PostgreSQL real.
 *
 * <p>Cada test corre dentro de una transacción que se revierte al terminar, así
 * que no se pisan entre sí ni dejan datos.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ResourcePersistenceIntegrationTest {

	@Autowired
	private ResourceRepository repository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void persistsAndReadsBackAResource() {
		Resource saved = repository.saveAndFlush(new Resource("Consultorio 4", "CONSULTORIO", 1));

		// Sin este clear() la lectura saldría del caché de primer nivel y
		// devolvería el mismo objeto en memoria, sin tocar la base. Entonces el
		// test pasaría incluso con el mapeo mal hecho.
		entityManager.clear();

		Resource found = repository.findById(saved.getId()).orElseThrow();

		assertThat(found.getName()).isEqualTo("Consultorio 4");
		assertThat(found.getType()).isEqualTo("CONSULTORIO");
		assertThat(found.getCapacity()).isEqualTo(1);
		assertThat(found.isActive()).isTrue();
	}

	@Test
	void assignsATimeOrderedUuidV7() {
		Resource saved = repository.saveAndFlush(new Resource("Cancha 2", "CANCHA", 1));

		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getId().version())
				.as("el id debe ser un UUID versión 7, ordenado por tiempo")
				.isEqualTo(7);
	}

	@Test
	void generatedIdsGrowMonotonically() {
		Resource first = repository.saveAndFlush(new Resource("Sala A", "SALA", 4));
		Resource second = repository.saveAndFlush(new Resource("Sala B", "SALA", 4));

		// Es la propiedad que hace que v7 no fragmente el índice: los inserts
		// caen al borde derecho del B-tree en lugar de en páginas al azar.
		assertThat(first.getId().toString()).isLessThan(second.getId().toString());
	}

	@Test
	void stampsTheAuditTimestamps() {
		Resource saved = repository.saveAndFlush(new Resource("Habitación 101", "HABITACION", 2));

		assertThat(saved.getCreatedAt()).isNotNull();
		assertThat(saved.getUpdatedAt()).isNotNull();
	}

	@Test
	void theDatabaseAlsoRejectsANonPositiveCapacity() {
		// Se inserta con SQL nativo, salteando la entidad a propósito: lo que se
		// verifica es que el CHECK de la base protege el invariante aunque
		// alguien escriba por fuera de la aplicación.
		assertThatThrownBy(() -> {
			entityManager.createNativeQuery("""
					INSERT INTO bookingcore_resource
					    (id, name, type, capacity, active, created_at, updated_at)
					VALUES (:id, 'Sala rota', 'SALA', 0, true, now(), now())
					""")
					.setParameter("id", UUID.randomUUID())
					.executeUpdate();
			entityManager.flush();
		}).hasStackTraceContaining("bookingcore_resource_capacity_positive");
	}

}

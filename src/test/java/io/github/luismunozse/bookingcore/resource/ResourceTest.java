package io.github.luismunozse.bookingcore.resource;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Invariantes del dominio, sin Spring ni base de datos.
 *
 * <p>Corren en milisegundos porque no necesitan nada del framework: las reglas
 * viven en la entidad, no en una capa de validación externa.
 */
class ResourceTest {

	@Test
	void isCreatedActive() {
		Resource resource = new Resource("Sala de reuniones A", "SALA", 8);

		assertThat(resource.isActive()).isTrue();
		assertThat(resource.getName()).isEqualTo("Sala de reuniones A");
		assertThat(resource.getType()).isEqualTo("SALA");
		assertThat(resource.getCapacity()).isEqualTo(8);
	}

	@Test
	void rejectsBlankName() {
		assertThatThrownBy(() -> new Resource("   ", "SALA", 1))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("name");
	}

	@Test
	void rejectsNullType() {
		assertThatThrownBy(() -> new Resource("Cancha 2", null, 1))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("type");
	}

	@Test
	void rejectsCapacityBelowOne() {
		assertThatThrownBy(() -> new Resource("Cancha 2", "CANCHA", 0))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("capacity");
	}

	@Test
	void trimsSurroundingWhitespace() {
		Resource resource = new Resource("  Consultorio 4  ", "  CONSULTORIO  ", 1);

		assertThat(resource.getName()).isEqualTo("Consultorio 4");
		assertThat(resource.getType()).isEqualTo("CONSULTORIO");
	}

	@Test
	void deactivateMarksItInactiveWithoutLosingData() {
		Resource resource = new Resource("Auto Toyota Corolla", "VEHICULO", 1);

		resource.deactivate();

		assertThat(resource.isActive()).isFalse();
		assertThat(resource.getName()).isEqualTo("Auto Toyota Corolla");
	}

	@Test
	void rejectsRenamingToBlank() {
		Resource resource = new Resource("Habitación 101", "HABITACION", 2);

		assertThatThrownBy(() -> resource.rename(""))
				.isInstanceOf(IllegalArgumentException.class);

		// La entidad rechazó el cambio sin quedar en un estado intermedio.
		assertThat(resource.getName()).isEqualTo("Habitación 101");
	}

	@Test
	void twoUnpersistedResourcesAreNeverEqual() {
		Resource one = new Resource("Sala A", "SALA", 4);
		Resource other = new Resource("Sala A", "SALA", 4);

		// Mismos datos, pero son dos recursos distintos: sin id no hay identidad.
		assertThat(one).isNotEqualTo(other);
		assertThat(one).isEqualTo(one);
	}

}

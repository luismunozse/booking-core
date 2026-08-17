package io.github.luismunozse.bookingcore.resource;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * Cualquier cosa que se pueda reservar: una sala, una cancha, un consultorio,
 * un vehículo o una persona.
 *
 * <p>El motor no conoce ninguno de esos dominios. {@code type} es una etiqueta
 * opaca que elige quien integra la librería, y BookingCore nunca ramifica según
 * su valor. Esa es la restricción que mantiene al motor agnóstico.
 */
@Entity
@Table(name = "bookingcore_resource")
public class Resource {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(nullable = false, length = 100)
	private String type;

	@Column(nullable = false)
	private int capacity;

	@Column(nullable = false)
	private boolean active;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(nullable = false)
	private Instant updatedAt;

	/**
	 * Requerido por Hibernate para instanciar la entidad por reflexión. Es
	 * protected y no público para que nadie lo use como forma legítima de crear
	 * un recurso: ese camino es el otro constructor, que garantiza invariantes.
	 */
	protected Resource() {
	}

	public Resource(String name, String type, int capacity) {
		this.name = requireText(name, "name");
		this.type = requireText(type, "type");
		this.capacity = requireValidCapacity(capacity);
		this.active = true;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getType() {
		return type;
	}

	public int getCapacity() {
		return capacity;
	}

	public boolean isActive() {
		return active;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void rename(String newName) {
		this.name = requireText(newName, "name");
	}

	public void changeCapacity(int newCapacity) {
		this.capacity = requireValidCapacity(newCapacity);
	}

	public void activate() {
		this.active = true;
	}

	/**
	 * Un recurso desactivado deja de aceptar reservas nuevas, pero conserva las
	 * que ya tiene. Esta es la alternativa deliberada a borrarlo.
	 */
	public void deactivate() {
		this.active = false;
	}

	private static String requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " es obligatorio y no puede estar vacío");
		}
		return value.strip();
	}

	private static int requireValidCapacity(int value) {
		if (value < 1) {
			throw new IllegalArgumentException("capacity debe ser al menos 1, pero fue " + value);
		}
		return value;
	}

	/**
	 * Igualdad basada solo en el identificador, nunca en los demás campos.
	 *
	 * <p>Dos entidades sin persistir jamás son iguales entre sí: solo lo son
	 * consigo mismas. Comparar por nombre o por tipo haría que dos recursos
	 * distintos con el mismo nombre se pisen dentro de un {@code Set}.
	 */
	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof Resource that)) {
			return false;
		}
		return id != null && id.equals(that.id);
	}

	/**
	 * Constante a propósito.
	 *
	 * <p>Hibernate asigna el id recién al persistir. Si el hash dependiera del
	 * id, un recurso agregado a un {@code Set} antes de persistirse cambiaría de
	 * hash después y quedaría inalcanzable dentro de esa colección. Un hash
	 * constante degrada el rendimiento del Set, pero nunca lo corrompe.
	 */
	@Override
	public int hashCode() {
		return Resource.class.hashCode();
	}

	@Override
	public String toString() {
		return "Resource[id=%s, name=%s, type=%s, capacity=%d, active=%s]"
				.formatted(id, name, type, capacity, active);
	}

}

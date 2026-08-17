package io.github.luismunozse.bookingcore.resource;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la petición para dar de alta un recurso.
 *
 * <p>Es un tipo distinto de la entidad a propósito: si el controlador recibiera
 * un {@code Resource}, el contrato HTTP quedaría atado al esquema de la base y
 * cualquier columna nueva se filtraría a la API sin que nadie lo decida.
 */
public record CreateResourceRequest(

		@NotBlank(message = "el nombre es obligatorio")
		@Size(max = 200, message = "el nombre no puede superar los 200 caracteres")
		String name,

		@NotBlank(message = "el tipo es obligatorio")
		@Size(max = 100, message = "el tipo no puede superar los 100 caracteres")
		String type,

		@Min(value = 1, message = "la capacidad debe ser al menos 1")
		Integer capacity) {

	/**
	 * La capacidad es opcional. La enorme mayoría de los recursos admite una
	 * sola reserva simultánea, y obligar a mandar {@code "capacity": 1} en cada
	 * alta sería ruido para quien integra la librería. Ausente equivale a 1.
	 */
	int capacityOrDefault() {
		return capacity != null ? capacity : 1;
	}

}

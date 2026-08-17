package io.github.luismunozse.bookingcore.resource;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de la petición para modificar un recurso.
 *
 * <p>Semántica de PATCH: un campo ausente o nulo significa "no lo cambies", no
 * "ponelo en null". Por eso ningún campo es obligatorio y {@code capacity} es
 * {@code Integer} y no {@code int}: hace falta poder distinguir el cero de la
 * ausencia.
 *
 * <p>El estado activo no se cambia por acá. Activar y desactivar son
 * operaciones de negocio con endpoints propios, no la mutación de un campo.
 */
public record UpdateResourceRequest(

		@Size(max = 200, message = "el nombre no puede superar los 200 caracteres")
		String name,

		@Min(value = 1, message = "la capacidad debe ser al menos 1")
		Integer capacity) {
}

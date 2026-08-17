package io.github.luismunozse.bookingcore.resource;

import java.time.Instant;
import java.util.UUID;

/**
 * Representación de un recurso en la API.
 *
 * <p>Es el servicio quien construye este tipo, y no el controlador, para que
 * las entidades nunca salgan de la transacción. Con Open Session In View
 * desactivado (docs/adr/0004), una entidad devuelta al controlador ya viene
 * desasociada y cualquier acceso perezoso fallaría.
 */
public record ResourceResponse(
		UUID id,
		String name,
		String type,
		int capacity,
		boolean active,
		Instant createdAt,
		Instant updatedAt) {

	static ResourceResponse from(Resource resource) {
		return new ResourceResponse(
				resource.getId(),
				resource.getName(),
				resource.getType(),
				resource.getCapacity(),
				resource.isActive(),
				resource.getCreatedAt(),
				resource.getUpdatedAt());
	}

}

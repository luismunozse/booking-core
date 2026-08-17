package io.github.luismunozse.bookingcore.resource;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Extiende {@link ErrorResponseException}, así que Spring ya sabe renderizarla
 * como {@code application/problem+json} sin necesidad de un handler propio.
 */
class ResourceNotFoundException extends ErrorResponseException {

	ResourceNotFoundException(UUID id) {
		super(HttpStatus.NOT_FOUND, problemFor(id), null);
	}

	private static ProblemDetail problemFor(UUID id) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.NOT_FOUND, "No existe un recurso con id " + id);
		problem.setTitle("Recurso no encontrado");
		// Un URN y no una URL: el campo "type" identifica la clase de problema
		// de forma estable, y BookingCore no es dueño de ningún dominio donde
		// alojar documentación. RFC 7807 permite que no sea desreferenciable.
		problem.setType(URI.create("urn:bookingcore:problem:resource-not-found"));
		return problem;
	}

}

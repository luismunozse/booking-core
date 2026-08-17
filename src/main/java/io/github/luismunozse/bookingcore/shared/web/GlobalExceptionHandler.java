package io.github.luismunozse.bookingcore.shared.web;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Contrato de errores de la API, en formato RFC 7807.
 *
 * <p>Hereda de {@link ResponseEntityExceptionHandler} para no reinventar el
 * manejo de las excepciones que Spring ya conoce; acá solo se enriquecen las
 * que devuelven poca información útil.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	/**
	 * La respuesta por defecto ante una validación fallida dice solo que la
	 * petición es inválida, sin indicar qué campo. Se agrega una propiedad
	 * {@code errors} con el detalle campo por campo, que es lo que necesita
	 * quien consume la API para corregir la llamada.
	 */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(
			MethodArgumentNotValidException ex,
			HttpHeaders headers,
			HttpStatusCode status,
			WebRequest request) {

		Map<String, String> fieldErrors = new LinkedHashMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
		}

		ProblemDetail problem = ex.getBody();
		problem.setTitle("Solicitud inválida");
		problem.setDetail("Uno o más campos no cumplen las restricciones");
		problem.setType(URI.create("urn:bookingcore:problem:validation-failed"));
		problem.setProperty("errors", fieldErrors);

		return handleExceptionInternal(ex, problem, headers, status, request);
	}

	/**
	 * Red de seguridad para los invariantes del dominio.
	 *
	 * <p>Bean Validation debería atajar casi todo en el borde, pero las reglas
	 * viven en los constructores de las entidades. Si alguna se escapa, el
	 * cliente recibe un 400 con el motivo y no un 500 opaco.
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail handleIllegalArgument(IllegalArgumentException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.BAD_REQUEST, exception.getMessage());
		problem.setTitle("Solicitud inválida");
		problem.setType(URI.create("urn:bookingcore:problem:invalid-request"));
		return problem;
	}

}

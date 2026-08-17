package io.github.luismunozse.bookingcore.resource;

import java.net.URI;
import java.util.UUID;

import io.github.luismunozse.bookingcore.shared.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/resources")
class ResourceController {

	private final ResourceService service;

	ResourceController(ResourceService service) {
		this.service = service;
	}

	@PostMapping
	ResponseEntity<ResourceResponse> create(@Valid @RequestBody CreateResourceRequest request,
			UriComponentsBuilder uriBuilder) {

		ResourceResponse created = service.create(request);
		URI location = uriBuilder.path("/api/v1/resources/{id}").buildAndExpand(created.id()).toUri();

		// 201 con Location: quien crea el recurso recibe dónde encontrarlo sin
		// tener que armar la URL por su cuenta.
		return ResponseEntity.created(location).body(created);
	}

	@GetMapping("/{id}")
	ResourceResponse findById(@PathVariable UUID id) {
		return service.findById(id);
	}

	/**
	 * Paginado desde el primer día: un listado sin techo funciona perfecto con
	 * diez recursos y tumba la aplicación con cien mil.
	 */
	@GetMapping
	PageResponse<ResourceResponse> findAll(
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return service.findAll(pageable);
	}

	@PatchMapping("/{id}")
	ResourceResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateResourceRequest request) {
		return service.update(id, request);
	}

	/**
	 * Endpoint propio en lugar de un PATCH sobre el campo {@code active}.
	 *
	 * <p>Desactivar no es cambiar un booleano: es una operación de negocio que
	 * va a tener reglas propias (qué pasa con las reservas futuras). Un endpoint
	 * con nombre expresa esa intención y deja lugar donde ponerlas.
	 */
	@PostMapping("/{id}/deactivate")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deactivate(@PathVariable UUID id) {
		service.deactivate(id);
	}

	@PostMapping("/{id}/activate")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void activate(@PathVariable UUID id) {
		service.activate(id);
	}

}

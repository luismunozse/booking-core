package io.github.luismunozse.bookingcore.resource;

import java.util.UUID;

import io.github.luismunozse.bookingcore.shared.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de recursos.
 *
 * <p>La clase entera es de solo lectura por defecto, y cada operación que
 * escribe lo declara explícitamente. Una transacción de solo lectura le permite
 * a Hibernate saltearse la detección de cambios y le avisa a PostgreSQL que no
 * va a haber escrituras; además vuelve visible, al leer el código, qué
 * operaciones modifican estado.
 */
@Service
@Transactional(readOnly = true)
class ResourceService {

	private final ResourceRepository repository;

	ResourceService(ResourceRepository repository) {
		this.repository = repository;
	}

	@Transactional
	ResourceResponse create(CreateResourceRequest request) {
		Resource resource = new Resource(request.name(), request.type(), request.capacityOrDefault());

		// saveAndFlush y no save: Hibernate genera createdAt y updatedAt al
		// sincronizar con la base, no al llamar a save(). Sin forzar ese
		// momento, la respuesta saldría con ambas marcas de tiempo en null.
		return ResourceResponse.from(repository.saveAndFlush(resource));
	}

	ResourceResponse findById(UUID id) {
		return ResourceResponse.from(requireExisting(id));
	}

	PageResponse<ResourceResponse> findAll(Pageable pageable) {
		return PageResponse.from(repository.findAll(pageable).map(ResourceResponse::from));
	}

	@Transactional
	ResourceResponse update(UUID id, UpdateResourceRequest request) {
		Resource resource = requireExisting(id);

		// Semántica de PATCH: lo que no viene, no se toca.
		if (request.name() != null) {
			resource.rename(request.name());
		}
		if (request.capacity() != null) {
			resource.changeCapacity(request.capacity());
		}

		// No hace falta llamar a save(): dentro de una transacción, Hibernate
		// detecta los cambios de una entidad gestionada y los sincroniza solo.
		// Pero sí hace falta forzar esa sincronización antes de armar la
		// respuesta: @UpdateTimestamp se aplica recién en ese momento, y sin
		// esto updatedAt saldría con el valor anterior.
		repository.flush();

		return ResourceResponse.from(resource);
	}

	@Transactional
	void activate(UUID id) {
		requireExisting(id).activate();
	}

	@Transactional
	void deactivate(UUID id) {
		requireExisting(id).deactivate();
	}

	private Resource requireExisting(UUID id) {
		return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException(id));
	}

}

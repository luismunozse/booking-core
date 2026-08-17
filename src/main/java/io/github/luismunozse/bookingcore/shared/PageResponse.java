package io.github.luismunozse.bookingcore.shared;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Página de resultados tal como la expone la API.
 *
 * <p>Existe para no serializar directamente el {@code Page} de Spring Data. Ese
 * tipo es una estructura interna del framework: su forma en JSON cambió entre
 * versiones, y atarle el contrato HTTP de una librería significaría que
 * actualizar Spring Data rompe a quien la integra.
 */
public record PageResponse<T>(
		List<T> content,
		int page,
		int size,
		long totalElements,
		int totalPages) {

	public static <T> PageResponse<T> from(Page<T> page) {
		return new PageResponse<>(
				page.getContent(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages());
	}

}

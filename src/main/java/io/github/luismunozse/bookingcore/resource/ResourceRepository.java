package io.github.luismunozse.bookingcore.resource;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Deliberadamente package-private.
 *
 * <p>Es la garantía concreta de la que habla docs/adr/0002: ningún otro paquete
 * puede alcanzar la tabla de recursos por atajo, porque el compilador se lo
 * impide. El acceso desde afuera va a pasar por el servicio de este mismo
 * paquete.
 */
interface ResourceRepository extends JpaRepository<Resource, UUID> {
}

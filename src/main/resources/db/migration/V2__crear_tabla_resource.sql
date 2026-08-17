-- Primera tabla de dominio de BookingCore.
--
-- El nombre lleva el prefijo bookingcore_ porque el motor está pensado para
-- embeberse en la base de datos de otra aplicación, donde "resource" es un
-- nombre demasiado genérico y chocaría con tablas existentes. Es la misma
-- convención que usan Quartz (QRTZ_), Spring Batch (BATCH_) y Flowable (ACT_).
-- Ver docs/adr/0005.

CREATE TABLE bookingcore_resource (
    id         uuid         PRIMARY KEY,
    name       varchar(200) NOT NULL,

    -- Etiqueta opaca para el integrador. El motor nunca ramifica según su
    -- valor, y por eso mismo no se valida contra ningún catálogo: hacerlo
    -- exigiría conocer el dominio de quien lo usa. Ver docs/adr/0007.
    type       varchar(100) NOT NULL,

    -- Cuántas reservas simultáneas admite el recurso. Todavía no lo consume
    -- nadie: la lógica de solapamiento llega en M3.
    capacity   integer      NOT NULL,

    -- Los recursos no se borran, se desactivan. Borrarlos rompería la
    -- integridad referencial y la historia de las reservas pasadas.
    active     boolean      NOT NULL,

    -- timestamptz almacena un instante en UTC, que es exactamente lo que
    -- representa un Instant de Java. El modelado de fechas de las reservas
    -- (que sí necesita husos horarios) se discute en M2.
    created_at timestamptz  NOT NULL,
    updated_at timestamptz  NOT NULL,

    -- Los mismos invariantes que protege la entidad, también en la base. Si
    -- alguien inserta por fuera de la aplicación, las reglas siguen valiendo.
    CONSTRAINT bookingcore_resource_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT bookingcore_resource_type_not_blank CHECK (btrim(type) <> ''),
    CONSTRAINT bookingcore_resource_capacity_positive CHECK (capacity >= 1)
);

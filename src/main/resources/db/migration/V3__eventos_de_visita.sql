-- =====================================================================
-- Bitacora de visitas: quien entra a la app del jugador y hasta donde
-- llega.
--
-- Hasta aca el sistema sabia contar reservas, que es el final del
-- recorrido, y nada de lo que pasa antes. Sin esto no hay forma de
-- responder la pregunta que importa -- de cada cien que abren la ficha
-- de un club, cuantos reservan -- ni de distinguir al que llego por la
-- busqueda global del que entro por el link directo del club.
--
-- Es propia y no un servicio de terceros por dos motivos concretos: el
-- CSP de la app del jugador solo admite scripts de su propio origen
-- (ver SecurityConfig), y la politica de privacidad promete que no hay
-- rastreo de terceros. Ademas la conversion vive en esta misma base,
-- asi que el embudo se arma con un JOIN y no con una integracion.
--
-- Columnas tipadas en vez de un jsonb de propiedades libres: el
-- catalogo de eventos es cerrado y chico, y asi las consultas del
-- embudo se escriben en SQL comun y el esquema valida lo que entra.
-- Las columnas que solo aplican a un evento quedan nulas en el resto,
-- que en Postgres no cuesta practicamente nada.
-- =====================================================================

CREATE TABLE page_event (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Sesion anonima del navegador: vive en sessionStorage y muere con la
    -- pestaña. No identifica a una persona ni sobrevive entre visitas; es
    -- solo el hilo que une los eventos de un mismo recorrido.
    session_id     UUID         NOT NULL,
    -- Orden dentro de la sesion, puesto por el navegador. created_at no
    -- alcanza: los eventos viajan en lotes y varios entran con el mismo
    -- instante de recepcion, que es justo cuando el orden importa (el
    -- embudo se lee en secuencia). Ademas hace idempotente la ingesta,
    -- porque sendBeacon puede reintentar un lote ya entregado.
    seq            INT          NOT NULL CHECK (seq > 0),

    name           VARCHAR(30)  NOT NULL,
    -- Ruta normalizada, nunca la URL cruda: /manage/:token y /confirm/:token
    -- llevan el token que ES la credencial del turno. Guardarlo aca lo
    -- duplicaria en una tabla sin proteccion. Lo normaliza el servidor
    -- aunque el navegador ya lo mande normalizado (PageEventService).
    path           VARCHAR(120) NOT NULL,

    -- Nulo en la portada y en la busqueda global, que no son de ningun club.
    -- Por eso la tabla no lleva @TenantId como el resto: el filtro de
    -- Hibernate exige un club siempre, y aca la mitad del embudo ocurre
    -- antes de que el jugador elija uno.
    club_id        UUID         REFERENCES tenant (id) ON DELETE CASCADE,

    -- Atribucion de origen. from_search: entro al club con el ?fecha=&hora=
    -- que arma la busqueda global. referrer_host y utm_source describen de
    -- donde llego al sitio, que es otra pregunta distinta.
    from_search    BOOLEAN      NOT NULL DEFAULT FALSE,
    referrer_host  VARCHAR(120),
    utm_source     VARCHAR(60),
    -- Lo deduce el servidor del User-Agent. El navegador no lo manda.
    device         VARCHAR(10),

    -- ------------------------------------------- propiedades por evento
    -- search: los filtros con los que busco y cuantos turnos le volvieron.
    -- results = 0 es el dato mas valioso de la tabla: demanda sin oferta.
    results        INT,
    search_date    DATE,
    time_from      TIME,
    time_to        TIME,
    clubs_filter   VARCHAR(200),

    -- Horario del turno que toco (search_result_click, slot_click,
    -- link_expired, waitlist_joined).
    slot_at        TIMESTAMPTZ,
    -- Paso del flujo de reserva dentro de la ficha del club: 1 dia,
    -- 2 hora, 3 datos.
    step           SMALLINT     CHECK (step BETWEEN 1 AND 3),
    payment_choice VARCHAR(20),
    -- Ata la sesion anonima con la reserva real: con esto se puede ver
    -- cuantos de los que entraron por la busqueda terminaron pagando. Sin
    -- clave foranea a proposito, aunque el valor salga de booking: el id lo
    -- manda el navegador, y con la foranea un id inventado hace fallar el
    -- INSERT y se lleva puesto el lote entero de eventos buenos. Una bitacora
    -- no puede rechazar una escritura por un dato que no gobierna; si el id no
    -- existe, el JOIN simplemente no encuentra nada, que es lo correcto.
    booking_id     UUID,
    -- Texto corto y acotado para el resto: el codigo de error de un
    -- checkout fallido, la posicion de un resultado en la lista.
    detail         VARCHAR(60),

    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- La tabla es append-only y nunca se actualiza una fila; la columna
    -- esta porque la comparten todas las entidades (BaseEntity).
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ux_page_event_session_seq UNIQUE (session_id, seq)
);

CREATE INDEX ix_page_event_club_time ON page_event (club_id, created_at);
CREATE INDEX ix_page_event_time ON page_event (created_at);

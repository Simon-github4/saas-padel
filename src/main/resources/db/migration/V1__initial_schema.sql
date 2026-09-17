-- =====================================================================
-- SaaS Padel - esquema inicial
--
-- Estrategia multi-tenant: Shared Database / Shared Schema. Toda tabla
-- transaccional lleva club_id y Hibernate lo filtra via @TenantId. Todos
-- los instantes se guardan en TIMESTAMPTZ (UTC); la zona horaria de
-- presentacion vive en tenant.time_zone.
--
-- Es el punto de partida de produccion: la fusion, en su estado final, de
-- todas las migraciones que se usaron durante el desarrollo. Produccion
-- arranca con una base vacia, asi que no hay historia que preservar ni
-- datos que convertir. Las migraciones que vengan despues se suman como
-- V2, V3, ... sin tocar este archivo.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ---------------------------------------------------------------- club
CREATE TABLE tenant (
    id                             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name                           VARCHAR(120) NOT NULL,
    slug                           VARCHAR(60)  NOT NULL UNIQUE,
    whatsapp_number                VARCHAR(25)  NOT NULL,
    time_zone                      VARCHAR(60)  NOT NULL DEFAULT 'America/Argentina/Buenos_Aires',
    -- MercadoPago se conecta por OAuth con PKCE desde el panel. Los tokens
    -- van cifrados en reposo por la aplicacion. No hay secreto de webhooks
    -- por club: todos los clubes cuelgan de la misma aplicacion de
    -- MercadoPago, que firma sus webhooks con un unico secreto
    -- (app.mercadopago.webhook-secret).
    mp_access_token                TEXT,
    mp_refresh_token               TEXT,
    mp_user_id                     VARCHAR(60),
    mp_token_expires_at            TIMESTAMPTZ,
    mp_connected_at                TIMESTAMPTZ,
    open_time                      TIME         NOT NULL DEFAULT '08:00',
    -- close_time <= open_time significa que el club cierra pasada la medianoche.
    close_time                     TIME         NOT NULL DEFAULT '23:59',
    default_slot_duration          INT          NOT NULL DEFAULT 90 CHECK (default_slot_duration BETWEEN 30 AND 240),
    cancellation_limit_hours       INT          NOT NULL DEFAULT 12 CHECK (cancellation_limit_hours >= 0),
    deposit_percentage             NUMERIC(5,2) NOT NULL DEFAULT 50.00 CHECK (deposit_percentage BETWEEN 0 AND 100),
    allow_unpaid_booking           BOOLEAN      NOT NULL DEFAULT TRUE,
    booking_horizon_days           INT          NOT NULL DEFAULT 21 CHECK (booking_horizon_days BETWEEN 1 AND 120),
    draft_ttl_minutes              INT          NOT NULL DEFAULT 10 CHECK (draft_ttl_minutes BETWEEN 5 AND 60),
    confirmation_ttl_minutes       INT          NOT NULL DEFAULT 15 CHECK (confirmation_ttl_minutes BETWEEN 5 AND 120),
    -- Techo de turnos futuros por telefono. Reservar bloquea la grilla sin pagar
    -- nada, asi que sin un limite cualquiera voltea la agenda del club en un
    -- minuto. Mismo default que Tenant.maxActiveBookings.
    max_active_bookings            INT          NOT NULL DEFAULT 7 CHECK (max_active_bookings BETWEEN 1 AND 50),
    active                         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at                     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Datos de portada para la app del jugador. Todo opcional salvo
    -- players_per_court, que siempre es 4 en padel pero como columna evita
    -- que el divisor del precio por persona quede como constante magica.
    tagline                        VARCHAR(160),
    address                        VARCHAR(200),
    city                           VARCHAR(100),
    latitude                       NUMERIC(9,6),
    longitude                      NUMERIC(9,6),
    -- Link a la ficha real del negocio en Google Maps, que GoogleMapsLinkResolver
    -- extrae del link de "Compartir" que pega el club. Nulo cuando ese link no
    -- permite identificar el lugar: Tenant.mapsUrl() cae entonces al link armado
    -- con las coordenadas (o con direccion y ciudad).
    google_maps_url                VARCHAR(500),
    -- Usuario de Instagram pelado (sin @ ni link), en minusculas; 30 es el largo
    -- maximo de Instagram. Tenant.instagramUrl() arma el link (ver InstagramHandles).
    instagram_handle               VARCHAR(30),
    -- URL externa de la foto de portada. Cuando el club sube un archivo en vez
    -- de pegar una URL, esos bytes viven aparte, en tenant_hero_image: tenant
    -- se carga en practicamente cualquier request del sistema y ninguno de esos
    -- caminos (salvo el que sirve la foto) necesita pagar el peso de una imagen.
    hero_image_url                 VARCHAR(500),
    players_per_court              INT          NOT NULL DEFAULT 4 CHECK (players_per_court BETWEEN 2 AND 8),
    -- Tarifa general del club, en pesos POR PERSONA. Si esta cargada, cualquier
    -- dia/franja sin una regla especifica se publica y se cobra con este precio.
    general_price_per_person       NUMERIC(12,2),
    -- hero_headline reemplaza al nombre cuando el club quiere titular con otra
    -- cosa ("JUGA AL LADO DEL MAR"). Null = usa el nombre. hero_cta_label es el
    -- texto del boton, null = "Ver horarios". hero_overlay es cuanto se
    -- oscurece la foto detras del texto: no es cosmetico, una foto clara deja
    -- el titulo ilegible y sin esta perilla el club no tiene como arreglarlo.
    hero_headline                  VARCHAR(80),
    hero_cta_label                 VARCHAR(40),
    hero_overlay                   INT          NOT NULL DEFAULT 55 CHECK (hero_overlay BETWEEN 0 AND 100),
    -- theme_mode: claro u oscuro, default oscuro. primary_color/secondary_color:
    -- acentos de marca, null = paleta de fabrica.
    theme_mode                     VARCHAR(10)  NOT NULL DEFAULT 'DARK' CHECK (theme_mode IN ('DARK', 'LIGHT')),
    primary_color                  VARCHAR(7)   CHECK (primary_color ~ '^#[0-9a-fA-F]{6}$'),
    secondary_color                VARCHAR(7)   CHECK (secondary_color ~ '^#[0-9a-fA-F]{6}$'),
    -- Que diseno de portada usa el club.
    hero_variant                   VARCHAR(20)  NOT NULL DEFAULT 'CLASSIC'
                                        CHECK (hero_variant IN ('CLASSIC', 'SCOREBOARD', 'COURT_SPLIT')),
    -- Algunos clubes no quieren el paso de confirmacion por WhatsApp para las
    -- reservas de palabra: prefieren que el turno quede firme apenas se carga,
    -- asumiendo el riesgo de ausentes a cambio de sacarle friccion al jugador.
    -- Default FALSE porque WhatsApp arranca apagado (ver AppProperties.Whatsapp).
    requires_booking_confirmation  BOOLEAN      NOT NULL DEFAULT FALSE
);

-- Servicios que destaca el club en la portada: contadores, estacionamiento,
-- alquiler de paletas, etc. Configurable por club porque cada uno ofrece lo suyo.
CREATE TABLE club_amenity (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id       UUID         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    icon          VARCHAR(40)  NOT NULL,
    title         VARCHAR(80)  NOT NULL,
    description   VARCHAR(160),
    display_order INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_club_amenity_club ON club_amenity (club_id);

-- Foto de portada subida como archivo, aparte de tenant (ver su comentario de
-- hero_image_url). Cuando en cambio el club pega una URL externa, esta tabla
-- queda vacia para ese club y hero_image_url apunta ahi directo.
CREATE TABLE tenant_hero_image (
    tenant_id     UUID PRIMARY KEY REFERENCES tenant (id) ON DELETE CASCADE,
    data          BYTEA        NOT NULL,
    content_type  VARCHAR(100) NOT NULL
);

-- Intercambio PKCE de MercadoPago. Fila de vida corta (10 minutos, lo que dura
-- el "code" de MercadoPago) entre "el dueno toco Conectar" y "MercadoPago
-- redirigio de vuelta". El code_verifier tiene que vivir en el servidor y nunca
-- en la URL que viaja por el navegador: es lo unico que impide que un code
-- interceptado a mitad de camino alcance para completar el intercambio
-- (RFC 7636). El state se guarda con huella, igual que el resto de los tokens
-- de un solo uso de la aplicacion (ver TokenHash).
CREATE TABLE mp_oauth_attempt (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    state_hash    VARCHAR(64) NOT NULL UNIQUE,
    tenant_id     UUID        NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    code_verifier TEXT        NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ------------------------------------------------------- usuarios panel
CREATE TABLE club_user (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    -- NULL = usuario de plataforma (soporte), no pertenece a ningun club.
    club_id       UUID         REFERENCES tenant (id) ON DELETE CASCADE,
    email         VARCHAR(160) NOT NULL,
    password_hash VARCHAR(120) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('SUPER_ADMIN', 'OWNER', 'STAFF')),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_club_user_email ON club_user (lower(email));
CREATE INDEX ix_club_user_club ON club_user (club_id);

-- -------------------------------------------------------------- canchas
CREATE TABLE court (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id       UUID        NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    name          VARCHAR(80) NOT NULL,
    display_order INT         NOT NULL DEFAULT 0,
    is_active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Paredes, piso y techo: el jugador puede buscar canchas por cada uno.
    -- OUTDOOR por defecto porque es la opcion que menos engana: una techada
    -- cargada como descubierta solo le falta a quien filtra por techo, pero una
    -- descubierta cargada como techada le promete un partido bajo la lluvia.
    wall          VARCHAR(10) NOT NULL DEFAULT 'GLASS'   CHECK (wall IN ('GLASS', 'WALL')),
    surface       VARCHAR(10) NOT NULL DEFAULT 'CARPET'  CHECK (surface IN ('CARPET', 'NO_CARPET')),
    roof          VARCHAR(10) NOT NULL DEFAULT 'OUTDOOR' CHECK (roof IN ('COVERED', 'OUTDOOR')),
    CONSTRAINT ux_court_club_name UNIQUE (club_id, name)
);
CREATE INDEX ix_court_club ON court (club_id);

-- ------------------------------------------------------ reglas de precio
-- price es el valor del TURNO COMPLETO en esa franja (grilla de bloques fijos).
-- court_id NULL aplica a todas las canchas; una regla con cancha explicita gana.
CREATE TABLE pricing_rule (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id     UUID          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    court_id    UUID          REFERENCES court (id) ON DELETE CASCADE,
    start_time  TIME          NOT NULL,
    end_time    TIME          NOT NULL,
    price       NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- Marca explicita de promocion: el admin tilda la franja en el panel y la
    -- app muestra el badge PROMO con su precio reducido.
    promo       BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT ck_pricing_rule_range CHECK (end_time > start_time)
);
CREATE INDEX ix_pricing_rule_club_start ON pricing_rule (club_id, start_time);

-- Dias de la semana que cubre cada regla: una regla puede abarcar una lista
-- (ej. lunes, martes y miercoles), no solo un dia.
CREATE TABLE pricing_rule_day (
    pricing_rule_id UUID NOT NULL REFERENCES pricing_rule (id) ON DELETE CASCADE,
    day_of_week     INT  NOT NULL CHECK (day_of_week BETWEEN 1 AND 7), -- ISO-8601: 1 = lunes
    PRIMARY KEY (pricing_rule_id, day_of_week)
);
CREATE INDEX ix_pricing_rule_day_lookup ON pricing_rule_day (day_of_week);

-- ------------------------------------------------------------- jugadores
CREATE TABLE customer (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id       UUID         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    phone_number  VARCHAR(25)  NOT NULL, -- normalizado a E.164
    full_name     VARCHAR(120) NOT NULL,
    is_trusted    BOOLEAN      NOT NULL DEFAULT FALSE,
    is_blocked    BOOLEAN      NOT NULL DEFAULT FALSE,
    no_show_count INT          NOT NULL DEFAULT 0,
    notes         TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_customer_club_phone UNIQUE (club_id, phone_number)
);
CREATE INDEX ix_customer_club ON customer (club_id);
-- findHistoryByPhone (portal de "mis turnos") cruza todos los tenants a
-- proposito y filtra solo por phone_number: el indice compuesto de arriba,
-- con club_id como columna lider, no le sirve.
CREATE INDEX ix_customer_phone ON customer (phone_number);

-- ------------------------------------------------------ cuenta del jugador
-- Login por email + contrasena (o Google), sin club_id: a diferencia de
-- customer (que es por club a proposito, ver su comentario de clase), esta
-- cuenta es global porque el mismo jugador reserva en varios clubes de la
-- plataforma y el historial tiene que verse junto. El telefono es opcional,
-- nadie lo verifica y NO es la identidad de la cuenta: la reserva y la lista
-- de espera recuerdan la cuenta de forma explicita (player_account_id).
--
-- Los tokens de sesion, de reseteo de contrasena y de confirmacion de alta
-- son credenciales, asi que se guardan como huella SHA-256 (TokenHash), nunca
-- en claro: una copia de la base no alcanza para entrar. SHA-256 y no bcrypt
-- porque son 32 bytes de SecureRandom y la busqueda es por igualdad. Las
-- columnas se llaman *_hash a proposito, para que nadie lea ese valor y lo
-- trate como el token de verdad.
CREATE TABLE player_account (
    id                                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number                         VARCHAR(25),
    last_login_at                        TIMESTAMPTZ,
    created_at                           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Se llena la primera vez que reserva con sesion iniciada; /account lo
    -- muestra y el checkout lo precarga.
    display_name                         VARCHAR(100),
    email                                VARCHAR(255),
    email_verified                       BOOLEAN      NOT NULL DEFAULT FALSE,
    password_hash                        VARCHAR(100),
    google_subject                       VARCHAR(255),
    password_reset_token_hash            VARCHAR(64),
    password_reset_token_expires_at      TIMESTAMPTZ,
    CONSTRAINT ux_player_account_phone UNIQUE (phone_number),
    CONSTRAINT ux_player_account_email UNIQUE (email),
    CONSTRAINT ux_player_account_google_subject UNIQUE (google_subject),
    CONSTRAINT ux_player_account_password_reset_token_hash UNIQUE (password_reset_token_hash)
);

-- Un registro todavia no confirmado. Si el jugador no lo confirma con el
-- codigo de 6 digitos o el link (los dos van en el mismo mail) dentro del
-- plazo, esta fila se descarta y ninguna cuenta llega a existir -- a
-- diferencia de player_account, que solo tiene filas de cuentas reales.
CREATE TABLE player_signup_pending (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email              VARCHAR(255) NOT NULL UNIQUE,
    password_hash      VARCHAR(100) NOT NULL,
    display_name       VARCHAR(100),
    phone_number       VARCHAR(25),
    code_hash          VARCHAR(100) NOT NULL,
    confirm_token_hash VARCHAR(64)  NOT NULL UNIQUE,
    expires_at         TIMESTAMPTZ  NOT NULL,
    attempts           INT          NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE player_session (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    player_id   UUID         NOT NULL REFERENCES player_account (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    CONSTRAINT ux_player_session_token_hash UNIQUE (token_hash)
);
CREATE INDEX ix_player_session_player ON player_session (player_id);

-- --------------------------------------------------------- turnos fijos
CREATE TABLE recurring_booking (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id          UUID          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    court_id         UUID          NOT NULL REFERENCES court (id) ON DELETE CASCADE,
    customer_id      UUID          NOT NULL REFERENCES customer (id),
    day_of_week      INT           NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    start_time       TIME          NOT NULL,
    duration_minutes INT           NOT NULL CHECK (duration_minutes BETWEEN 30 AND 240),
    valid_from       DATE          NOT NULL,
    valid_until      DATE, -- NULL = indefinido
    price_override   NUMERIC(12,2) CHECK (price_override IS NULL OR price_override >= 0),
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    notes            TEXT,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_recurring_validity CHECK (valid_until IS NULL OR valid_until >= valid_from)
);
CREATE INDEX ix_recurring_club_active ON recurring_booking (club_id, active);

-- Semanas puntuales en las que el grupo avisa que no juega.
CREATE TABLE recurring_booking_skip (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    recurring_booking_id UUID        NOT NULL REFERENCES recurring_booking (id) ON DELETE CASCADE,
    skip_date            DATE        NOT NULL,
    reason               VARCHAR(160),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_recurring_skip UNIQUE (recurring_booking_id, skip_date)
);

-- -------------------------------------------------------------- reservas
CREATE TABLE booking (
    id                      UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id                 UUID          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    court_id                UUID          NOT NULL REFERENCES court (id),
    customer_id             UUID          NOT NULL REFERENCES customer (id),
    recurring_booking_id    UUID          REFERENCES recurring_booking (id) ON DELETE SET NULL,
    -- Cuenta de jugador que hizo la reserva: es la identidad con la que el
    -- historial de /account cruza clubes (nunca el telefono, que cualquiera
    -- puede escribir). Nulo para las reservas de invitado, que siguen siendo el
    -- camino principal: reservar no pide cuenta.
    player_account_id       UUID          REFERENCES player_account (id) ON DELETE SET NULL,
    -- El nombre que escribio quien reservo. El nombre del customer no lo pisa
    -- cualquiera (CustomerService.findOrCreate), y este queda en el turno para
    -- que diga quien reservo de verdad. Nulo en los turnos que genera un turno
    -- fijo: esos muestran el del jugador.
    booked_name             VARCHAR(120),
    start_time              TIMESTAMPTZ   NOT NULL,
    end_time                TIMESTAMPTZ   NOT NULL,
    status                  VARCHAR(25)   NOT NULL CHECK (status IN
                                ('DRAFT', 'AWAITING_CONFIRMATION', 'CONFIRMED',
                                 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
    source                  VARCHAR(20)   NOT NULL DEFAULT 'WEB'
                                CHECK (source IN ('WEB', 'ADMIN', 'RECURRING')),
    total_price             NUMERIC(12,2) NOT NULL CHECK (total_price >= 0),
    deposit_amount          NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (deposit_amount >= 0),
    paid_amount             NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (paid_amount >= 0),
    management_token        VARCHAR(64)   UNIQUE,
    -- Token de solo lectura para compartir el turno con otros jugadores: a
    -- proposito distinto del de gestion, para que compartirlo no le de a
    -- quien lo recibe la posibilidad de cancelar.
    share_token             VARCHAR(64)   UNIQUE,
    confirmation_token      VARCHAR(64)   UNIQUE,
    confirmation_expires_at TIMESTAMPTZ,
    -- Vencimiento del DRAFT mientras se espera el pago en MercadoPago.
    draft_expires_at        TIMESTAMPTZ,
    cancelled_at            TIMESTAMPTZ,
    cancellation_reason     VARCHAR(40),
    admin_notes             TEXT,
    version                 BIGINT        NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_booking_range CHECK (end_time > start_time)
);

CREATE INDEX ix_booking_club_start ON booking (club_id, start_time);
CREATE INDEX ix_booking_court_start ON booking (court_id, start_time);
CREATE INDEX ix_booking_status_expiry ON booking (status, draft_expires_at, confirmation_expires_at);
CREATE INDEX ix_booking_customer ON booking (customer_id);
-- El historial del jugador entra por esta columna, cruzando todos los clubes.
CREATE INDEX ix_booking_player_account ON booking (player_account_id, start_time DESC);

-- Unica garantia real contra el doble booking: dos requests concurrentes que
-- pasen la validacion aplicativa chocan aca, en la base.
-- CANCELLED y NO_SHOW quedan fuera para que el club pueda revender el turno.
ALTER TABLE booking
    ADD CONSTRAINT ex_booking_no_overlap
    EXCLUDE USING gist (
        court_id WITH =,
        tstzrange(start_time, end_time, '[)') WITH &&
    ) WHERE (status IN ('DRAFT', 'AWAITING_CONFIRMATION', 'CONFIRMED', 'COMPLETED'));

-- ---------------------------------------------------- bloqueos operativos
CREATE TABLE blackout (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id    UUID        NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    court_id   UUID        REFERENCES court (id) ON DELETE CASCADE, -- NULL = todas las canchas
    start_time TIMESTAMPTZ NOT NULL,
    end_time   TIMESTAMPTZ NOT NULL,
    reason     VARCHAR(160),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_blackout_range CHECK (end_time > start_time)
);
CREATE INDEX ix_blackout_club_start ON blackout (club_id, start_time);

-- ------------------------------------------------------- pedidos de buffet
-- El buffet tambien le vende a quien no juega: el que viene a mirar, el
-- acompanante. Un pedido es un nombre -"a nombre de" quien se pidio-, los
-- productos que se le van sumando y los cobros, igual que un turno.
-- customer_name es nullable: una venta rapida que se cobra en el momento no
-- necesita nombre. Para dejar algo en la cuenta si es obligatorio, y eso lo
-- controla la aplicacion.
CREATE TABLE buffet_order (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id       UUID          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    customer_name VARCHAR(120),
    total_price   NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (total_price >= 0),
    paid_amount   NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (paid_amount >= 0),
    registered_by UUID          REFERENCES club_user (id) ON DELETE SET NULL,
    version       BIGINT        NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
-- La caja lista los pedidos del dia por fecha de creacion.
CREATE INDEX ix_buffet_order_club_created ON buffet_order (club_id, created_at);

-- ---------------------------------------------------------------- pagos
-- Un cobro es de un turno o de un pedido de buffet, nunca de los dos ni de ninguno.
CREATE TABLE payment (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id          UUID          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    booking_id       UUID          REFERENCES booking (id) ON DELETE CASCADE,
    buffet_order_id  UUID          REFERENCES buffet_order (id) ON DELETE CASCADE,
    -- <> 0 y no >= 0: una devolucion de mostrador (ej. sacar un producto ya
    -- cobrado) se registra como un Payment con monto negativo en vez de un
    -- movimiento aparte, asi el saldo y el desglose por metodo de las
    -- estadisticas la netean solos, sin casos especiales en ningun lado.
    amount           NUMERIC(12,2) NOT NULL CHECK (amount <> 0),
    method           VARCHAR(20)   NOT NULL CHECK (method IN ('MERCADOPAGO', 'CASH', 'TRANSFER')),
    status           VARCHAR(20)   NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'REFUNDED')),
    -- Id de la order de MercadoPago (API de Orders) con la que arranca el pago.
    mp_order_id      VARCHAR(120),
    -- Unico: hace idempotente el reprocesamiento del webhook de MercadoPago.
    mp_payment_id    VARCHAR(60)   UNIQUE,
    raw_payload      TEXT,
    registered_by    UUID          REFERENCES club_user (id) ON DELETE SET NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_payment_owner CHECK ((booking_id IS NULL) <> (buffet_order_id IS NULL))
);
CREATE INDEX ix_payment_booking ON payment (booking_id);
CREATE INDEX ix_payment_buffet_order ON payment (buffet_order_id);
CREATE INDEX ix_payment_club_created ON payment (club_id, created_at);
CREATE INDEX ix_payment_order ON payment (mp_order_id);

-- ------------------------------------------------ productos del buffet
-- product: catalogo del club (ej. "Gatorade", "Agua"). Se desactiva en vez de
-- borrarse, para no perder el historial de ventas ya cargadas.
CREATE TABLE product (
    id         UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id    UUID          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    name       VARCHAR(80)   NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0),
    is_active  BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ux_product_club_name UNIQUE (club_id, name)
);
CREATE INDEX ix_product_club ON product (club_id);

-- Una linea vendida, con nombre y precio "congelados" al momento de la venta.
-- Es de un turno o de un pedido de buffet, nunca de los dos ni de ninguno. Su
-- importe se suma directo al total_price del turno o del pedido: el saldo y el
-- cobro de mostrador no necesitan saber que esto existe.
CREATE TABLE product_sale (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id         UUID          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    booking_id      UUID          REFERENCES booking (id) ON DELETE CASCADE,
    buffet_order_id UUID          REFERENCES buffet_order (id) ON DELETE CASCADE,
    product_id      UUID          NOT NULL REFERENCES product (id),
    product_name    VARCHAR(80)   NOT NULL,
    unit_price      NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0),
    quantity        INT           NOT NULL CHECK (quantity > 0),
    registered_by   UUID          REFERENCES club_user (id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_product_sale_owner CHECK ((booking_id IS NULL) <> (buffet_order_id IS NULL))
);
CREATE INDEX ix_product_sale_booking ON product_sale (booking_id);
CREATE INDEX ix_product_sale_buffet_order ON product_sale (buffet_order_id);
CREATE INDEX ix_product_sale_club_created ON product_sale (club_id, created_at);

-- ------------------------------------------------- alertas para el panel
CREATE TABLE operational_alert (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id     UUID        NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    booking_id  UUID        REFERENCES booking (id) ON DELETE CASCADE,
    -- WAITLIST_SLOT_FREED: un jugador cancelo por la web un turno con gente en
    -- la lista de espera (AlertService.waitlistSlotFreed).
    type        VARCHAR(40) NOT NULL,
    message     TEXT        NOT NULL,
    resolved    BOOLEAN     NOT NULL DEFAULT FALSE,
    resolved_at TIMESTAMPTZ,
    resolved_by UUID        REFERENCES club_user (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT operational_alert_type_check CHECK (type IN
        ('REFUND_REQUIRED', 'ORPHAN_PAYMENT', 'NOTIFICATION_FAILED', 'RECURRING_CONFLICT',
         'WAITLIST_SLOT_FREED'))
);
CREATE INDEX ix_alert_club_pending ON operational_alert (club_id, resolved, created_at DESC);

-- --------------------------------------------- trazabilidad de WhatsApps
CREATE TABLE notification_log (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id             UUID        NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    booking_id          UUID        REFERENCES booking (id) ON DELETE SET NULL,
    phone_number        VARCHAR(25) NOT NULL,
    template            VARCHAR(60) NOT NULL,
    body                TEXT,
    status              VARCHAR(20) NOT NULL CHECK (status IN ('SENT', 'FAILED', 'SKIPPED')),
    provider_message_id VARCHAR(120),
    error               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_notification_club_created ON notification_log (club_id, created_at DESC);

-- --------------------------------------------------- lista de espera de turnos
CREATE TABLE waitlist_entry (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id           UUID        NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    customer_id       UUID        NOT NULL REFERENCES customer (id) ON DELETE CASCADE,
    -- Cuenta de quien se anoto (anotarse exige sesion, ver WaitlistService.join):
    -- es lo que deja a "Mis turnos" listar sus anotaciones en todos los clubes.
    -- ON DELETE SET NULL, igual que booking.player_account_id: si la cuenta se
    -- borra, la anotacion sigue y el aviso igual puede salir.
    player_account_id UUID        REFERENCES player_account (id) ON DELETE SET NULL,
    -- Mail de respaldo para el aviso cuando el WhatsApp del club esta apagado o
    -- el envio fallo. Congelado en el momento del alta -mismo criterio que
    -- ProductSale con nombre y precio-, no una referencia viva a la cuenta.
    email             VARCHAR(255),
    starts_at         TIMESTAMPTZ NOT NULL,
    ends_at           TIMESTAMPTZ NOT NULL,
    notified          BOOLEAN     NOT NULL DEFAULT FALSE,
    notified_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_waitlist_slot UNIQUE (club_id, customer_id, starts_at)
);
CREATE INDEX ix_waitlist_pending ON waitlist_entry (club_id, notified, starts_at);
CREATE INDEX ix_waitlist_player_account ON waitlist_entry (player_account_id, starts_at);

-- =====================================================================
-- Bitacora de visitas: quien entra a la app del jugador y hasta donde
-- llega, para armar el embudo (de cada cien que abren la ficha de un club,
-- cuantos reservan) y distinguir al que llego por la busqueda global del
-- que entro por el link directo del club.
--
-- Es propia y no un servicio de terceros: el CSP de la app del jugador
-- solo admite scripts de su propio origen (ver SecurityConfig), y la
-- politica de privacidad promete que no hay rastreo de terceros.
--
-- Columnas tipadas en vez de un jsonb de propiedades libres: el catalogo
-- de eventos es cerrado y chico, y asi las consultas del embudo se
-- escriben en SQL comun. Las columnas que solo aplican a un evento quedan
-- nulas en el resto.
-- =====================================================================
CREATE TABLE page_event (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Sesion anonima del navegador: vive en sessionStorage y muere con la
    -- pestana. No identifica a una persona ni sobrevive entre visitas; es
    -- solo el hilo que une los eventos de un mismo recorrido.
    session_id     UUID         NOT NULL,
    -- Orden dentro de la sesion, puesto por el navegador. created_at no
    -- alcanza: los eventos viajan en lotes y varios entran con el mismo
    -- instante de recepcion. Ademas hace idempotente la ingesta, porque
    -- sendBeacon puede reintentar un lote ya entregado.
    seq            INT          NOT NULL CHECK (seq > 0),

    name           VARCHAR(30)  NOT NULL,
    -- Ruta normalizada, nunca la URL cruda: /manage/:token y /confirm/:token
    -- llevan el token que ES la credencial del turno. Lo normaliza el
    -- servidor (PageEventService).
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
    -- Ata la sesion anonima con la reserva real. Sin clave foranea a
    -- proposito: el id lo manda el navegador, y con la foranea un id
    -- inventado hace fallar el INSERT y se lleva puesto el lote entero de
    -- eventos buenos. Si el id no existe, el JOIN simplemente no encuentra nada.
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

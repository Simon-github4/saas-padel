-- =====================================================================
-- SaaS Padel - esquema inicial
-- Estrategia multi-tenant: Shared Database / Shared Schema.
-- Toda tabla transaccional lleva club_id y Hibernate lo filtra via @TenantId.
-- Todos los instantes se guardan en TIMESTAMPTZ (UTC); la zona horaria de
-- presentacion vive en tenant.time_zone.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ---------------------------------------------------------------- club
CREATE TABLE tenant (
    id                        UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name                      VARCHAR(120) NOT NULL,
    slug                      VARCHAR(60)  NOT NULL UNIQUE,
    whatsapp_number           VARCHAR(25)  NOT NULL,
    time_zone                 VARCHAR(60)  NOT NULL DEFAULT 'America/Argentina/Buenos_Aires',
    -- Credenciales de MercadoPago: cifradas en reposo por la aplicacion.
    mp_access_token           TEXT,
    mp_webhook_secret         TEXT,
    open_time                 TIME         NOT NULL DEFAULT '08:00',
    -- close_time <= open_time significa que el club cierra pasada la medianoche.
    close_time                TIME         NOT NULL DEFAULT '23:59',
    default_slot_duration     INT          NOT NULL DEFAULT 90 CHECK (default_slot_duration BETWEEN 30 AND 240),
    cancellation_limit_hours  INT          NOT NULL DEFAULT 12 CHECK (cancellation_limit_hours >= 0),
    deposit_percentage        NUMERIC(5,2) NOT NULL DEFAULT 50.00 CHECK (deposit_percentage BETWEEN 0 AND 100),
    allow_unpaid_booking      BOOLEAN      NOT NULL DEFAULT TRUE,
    booking_horizon_days      INT          NOT NULL DEFAULT 21 CHECK (booking_horizon_days BETWEEN 1 AND 120),
    draft_ttl_minutes         INT          NOT NULL DEFAULT 10 CHECK (draft_ttl_minutes BETWEEN 5 AND 60),
    confirmation_ttl_minutes  INT          NOT NULL DEFAULT 15 CHECK (confirmation_ttl_minutes BETWEEN 5 AND 120),
    active                    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT now()
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
    day_of_week INT           NOT NULL CHECK (day_of_week BETWEEN 1 AND 7), -- ISO-8601: 1 = lunes
    start_time  TIME          NOT NULL,
    end_time    TIME          NOT NULL,
    price       NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_pricing_rule_range CHECK (end_time > start_time)
);
CREATE INDEX ix_pricing_rule_lookup ON pricing_rule (club_id, day_of_week, start_time);

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

-- ---------------------------------------------------------------- pagos
CREATE TABLE payment (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id          UUID          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    booking_id       UUID          NOT NULL REFERENCES booking (id) ON DELETE CASCADE,
    amount           NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    method           VARCHAR(20)   NOT NULL CHECK (method IN ('MERCADOPAGO', 'CASH')),
    status           VARCHAR(20)   NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'REFUNDED')),
    mp_preference_id VARCHAR(120),
    -- Unico: hace idempotente el reprocesamiento del webhook de MercadoPago.
    mp_payment_id    VARCHAR(60)   UNIQUE,
    raw_payload      TEXT,
    registered_by    UUID          REFERENCES club_user (id) ON DELETE SET NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_payment_booking ON payment (booking_id);
CREATE INDEX ix_payment_club_created ON payment (club_id, created_at);
CREATE INDEX ix_payment_preference ON payment (mp_preference_id);

-- ------------------------------------------------- alertas para el panel
CREATE TABLE operational_alert (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id     UUID        NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    booking_id  UUID        REFERENCES booking (id) ON DELETE CASCADE,
    type        VARCHAR(40) NOT NULL CHECK (type IN
                    ('REFUND_REQUIRED', 'ORPHAN_PAYMENT', 'NOTIFICATION_FAILED', 'RECURRING_CONFLICT')),
    message     TEXT        NOT NULL,
    resolved    BOOLEAN     NOT NULL DEFAULT FALSE,
    resolved_at TIMESTAMPTZ,
    resolved_by UUID        REFERENCES club_user (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
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

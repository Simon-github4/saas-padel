-- Modulo gimnasio: socios con DNI, membresias por periodo y check-in por QR.
--
-- Todo cuelga de tablas gym_* y del club (tenant): ninguna tabla de padel
-- apunta hacia aca, asi el modulo se puede apagar o extraer sin tocar el resto.
-- Hacia afuera solo hay FKs a tenant y a club_user (quien registro el cobro).
--
-- Las FKs entre tablas gym_* son DEFERRABLE INITIALLY DEFERRED: se verifican al
-- commit y no fila por fila. Borrar un club arrastra todo por ON DELETE CASCADE
-- de club_id, pero Postgres ejecuta cada cascada como una sentencia interna
-- aparte (primero gym_sede, despues gym_membership...), y una FK comun se
-- chequea al terminar CADA una: la sede se borra mientras todavia hay cuotas que
-- la referencian y falla. Diferidas, se chequean cuando ya se borro todo. Borrar
-- a mano una sede con historial sigue fallando, que es lo que se quiere.

-- ------------------------------------------------------- activacion por club
-- Existir una fila con enabled = TRUE es lo que prende el modulo para el club.
-- No se agrega una columna a tenant para no tocar el modelo de padel.
CREATE TABLE gym_club_config (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id    UUID        NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    -- FALSE (lo normal): el socio entra con solo el DNI. TRUE: DNI + clave, con la
    -- clave temporal del mostrador. Se puede cambiar cuando se quiera.
    password_required BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_gym_club_config_club UNIQUE (club_id)
);

-- ------------------------------------------------------------------- sedes
-- Un gimnasio con una sola sede es el caso comun; un club con dos gimnasios
-- que comparten membresia tiene dos filas. El qr_token es lo que codifica el
-- QR impreso en la puerta: es un secreto estatico y regenerable, y va en claro
-- (no como huella) porque el panel tiene que poder volver a dibujar el QR.
CREATE TABLE gym_sede (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id           UUID          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    name              VARCHAR(120)  NOT NULL,
    address           VARCHAR(200),
    qr_token          VARCHAR(64)   NOT NULL,
    is_active         BOOLEAN       NOT NULL DEFAULT TRUE,
    -- Donde queda la sede. Si estan cargadas, el socio tiene que estar a menos de
    -- radius_meters para registrar el ingreso: es lo que impide hacerlo desde lejos
    -- con una foto del QR. Sin coordenadas, la sede no verifica la ubicacion.
    -- El radio es holgado a proposito (200 m): el GPS falla adentro de un edificio.
    latitude          DOUBLE PRECISION CHECK (latitude BETWEEN -90 AND 90),
    longitude         DOUBLE PRECISION CHECK (longitude BETWEEN -180 AND 180),
    radius_meters     INT           NOT NULL DEFAULT 200 CHECK (radius_meters BETWEEN 20 AND 5000),
    -- Porcentaje que le corresponde a un socio externo sobre lo atribuido a
    -- esta sede (liquidacion). 0 = la sede es toda del club.
    partner_share_pct NUMERIC(5,2)  NOT NULL DEFAULT 0 CHECK (partner_share_pct BETWEEN 0 AND 100),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_gym_sede_location CHECK ((latitude IS NULL) = (longitude IS NULL)),
    CONSTRAINT ux_gym_sede_club_name UNIQUE (club_id, name),
    CONSTRAINT ux_gym_sede_qr_token UNIQUE (qr_token)
);
CREATE INDEX ix_gym_sede_club ON gym_sede (club_id);

-- ------------------------------------------------------------------ socios
-- El socio entra con su DNI (y, si el club lo pide, con la clave que le da el
-- mostrador). Es del club, no global: el mismo DNI en dos clubes son dos socios
-- independientes.
CREATE TABLE gym_member (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id              UUID         NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    dni                  VARCHAR(12)  NOT NULL CHECK (dni ~ '^[0-9]{6,10}$'),
    full_name            VARCHAR(120) NOT NULL,
    phone                VARCHAR(25),
    -- Solo cuenta si el club pide clave (gym_club_config.password_required). Se
    -- guarda siempre, aunque el club no la pida, para poder prenderlo despues.
    password_hash        VARCHAR(100) NOT NULL,
    -- TRUE mientras siga con la clave temporal que le dieron en el mostrador.
    must_change_password BOOLEAN      NOT NULL DEFAULT TRUE,
    is_enabled           BOOLEAN      NOT NULL DEFAULT TRUE,
    notes                TEXT,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_gym_member_club_dni UNIQUE (club_id, dni)
);
CREATE INDEX ix_gym_member_club ON gym_member (club_id);

-- Sesion de la app del socio: token opaco, guardado con huella (ver TokenHash).
CREATE TABLE gym_session (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id    UUID        NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    member_id  UUID        NOT NULL REFERENCES gym_member (id) DEFERRABLE INITIALLY DEFERRED,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_gym_session_token UNIQUE (token_hash)
);
CREATE INDEX ix_gym_session_member ON gym_session (member_id);
CREATE INDEX ix_gym_session_club ON gym_session (club_id);

-- ------------------------------------------------------------- membresias
-- Un periodo pago: desde starts_on hasta ends_on inclusive, con un tope de dias
-- por semana. Renovar es una fila nueva, asi queda el historial. Los datos del
-- cobro van en la misma fila: se cobra en el mostrador, de una sola vez.
CREATE TABLE gym_membership (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id           UUID          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    member_id         UUID          NOT NULL REFERENCES gym_member (id) DEFERRABLE INITIALLY DEFERRED,
    starts_on         DATE          NOT NULL,
    ends_on           DATE          NOT NULL,
    days_per_week     INT           NOT NULL CHECK (days_per_week BETWEEN 1 AND 7),
    price             NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    pay_method        VARCHAR(20)   NOT NULL CHECK (pay_method IN ('CASH', 'TRANSFER')),
    -- Donde se cobro. No es lo mismo que a que sede se le atribuye el ingreso:
    -- eso lo decide la asistencia (liquidacion).
    collected_sede_id UUID          NOT NULL REFERENCES gym_sede (id) DEFERRABLE INITIALLY DEFERRED,
    registered_by     UUID          REFERENCES club_user (id) ON DELETE SET NULL,
    voided_at         TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_gym_membership_period CHECK (ends_on >= starts_on),
    -- Un socio no puede tener dos periodos vigentes que se pisen. Una membresia
    -- anulada deja de contar.
    CONSTRAINT ex_gym_membership_overlap EXCLUDE USING gist (
        member_id WITH =,
        daterange(starts_on, ends_on, '[]') WITH &&
    ) WHERE (voided_at IS NULL)
);
CREATE INDEX ix_gym_membership_club ON gym_membership (club_id);
CREATE INDEX ix_gym_membership_member ON gym_membership (member_id, starts_on);

-- Sedes en las que vale la membresia ("Full" = todas, o solo una).
CREATE TABLE gym_membership_sede (
    membership_id UUID NOT NULL REFERENCES gym_membership (id) ON DELETE CASCADE,
    sede_id       UUID NOT NULL REFERENCES gym_sede (id) DEFERRABLE INITIALLY DEFERRED,
    PRIMARY KEY (membership_id, sede_id)
);

-- --------------------------------------------------------------- ingresos
-- local_date es el dia calendario en la zona horaria del club: el "un ingreso
-- por dia" y el tope semanal se cuentan sobre este campo, no sobre el instante.
CREATE TABLE gym_checkin (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id        UUID        NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    member_id      UUID        NOT NULL REFERENCES gym_member (id) DEFERRABLE INITIALLY DEFERRED,
    membership_id  UUID        NOT NULL REFERENCES gym_membership (id) DEFERRABLE INITIALLY DEFERRED,
    sede_id        UUID        NOT NULL REFERENCES gym_sede (id) DEFERRABLE INITIALLY DEFERRED,
    checked_in_at  TIMESTAMPTZ NOT NULL,
    local_date     DATE        NOT NULL,
    -- A cuantos metros de la sede estaba el celular al registrarse. NULL si no se
    -- verifico (sede sin coordenadas, o ingreso cargado a mano). Queda de constancia
    -- por si hay que revisar un ingreso.
    distance_m     INT,
    -- TRUE cuando el mostrador dejo pasar a alguien que ya habia usado sus
    -- dias de la semana.
    is_override    BOOLEAN     NOT NULL DEFAULT FALSE,
    -- El usuario del panel que lo cargo a mano; NULL si el socio escaneo el QR.
    registered_by  UUID        REFERENCES club_user (id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_gym_checkin_member_day UNIQUE (member_id, local_date)
);
CREATE INDEX ix_gym_checkin_club_sede_day ON gym_checkin (club_id, sede_id, local_date);

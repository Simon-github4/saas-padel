-- =====================================================================
-- MercadoPago pasa de token pegado a mano a OAuth con PKCE.
--
-- El club ya no copia su access token: autoriza la conexion desde el panel
-- y el intercambio lo completa la aplicacion. Eso trae credenciales que
-- antes no existian (refresh token, id de usuario de MercadoPago, cuando
-- vence el access token, cuando se conecto) y saca una que ya no tiene
-- sentido por club: con OAuth todos los clubes cuelgan de la misma
-- aplicacion de MercadoPago (Tus integraciones), y esa aplicacion firma
-- sus webhooks con un unico secreto -- no hay uno por club para guardar.
--
-- mp_access_token se reutiliza tal cual: sigue siendo el access token con
-- el que se cobra, solo que ahora lo entrega el intercambio OAuth en vez
-- de un campo de texto en Configuracion.
-- =====================================================================

ALTER TABLE tenant
    ADD COLUMN mp_refresh_token    TEXT,
    ADD COLUMN mp_user_id          VARCHAR(60),
    ADD COLUMN mp_token_expires_at TIMESTAMPTZ,
    ADD COLUMN mp_connected_at     TIMESTAMPTZ,
    DROP COLUMN mp_webhook_secret;

-- ------------------------------------------------- intercambio PKCE
-- Fila de vida corta (10 minutos, lo que dura el "code" de MercadoPago)
-- entre "el dueno toco Conectar" y "MercadoPago redirigio de vuelta". El
-- code_verifier tiene que vivir en el servidor y nunca en la URL que viaja
-- por el navegador: es lo unico que impide que un code interceptado a mitad
-- de camino alcance para completar el intercambio (RFC 7636). El state se
-- guarda con huella, igual que el resto de los tokens de un solo uso de la
-- aplicacion (ver TokenHash): el valor que vuelve del navegador no sirve
-- de nada por si solo -- sin el code que unicamente entrega MercadoPago no
-- hay intercambio posible -- pero tampoco hay motivo para guardarlo en claro.
CREATE TABLE mp_oauth_attempt (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    state_hash    VARCHAR(64) NOT NULL UNIQUE,
    tenant_id     UUID        NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    code_verifier TEXT        NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

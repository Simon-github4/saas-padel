-- "Recordarme" del panel: el dispositivo vuelve a entrar solo, sin pedir la
-- clave, durante 15 dias desde que se ingreso con ella.
--
-- Sin esto la sesion se perdia a los 30 minutos sin uso y con cada deploy (vive
-- en la memoria del proceso), y el que lleva la agenda en el celular tenia que
-- volver a escribir la clave varias veces por dia.
--
-- Como player_session: se guarda la huella SHA-256 del token, nunca el token. El
-- valor que abre el panel solo existe en la cookie del navegador. Una fila se
-- borra al salir, al vencer, al cambiar la clave o al deshabilitar al usuario.

CREATE TABLE panel_remember_token (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES club_user (id) ON DELETE CASCADE,
    token_hash   VARCHAR(64)  NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL,
    last_used_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_panel_remember_token_hash UNIQUE (token_hash)
);
CREATE INDEX ix_panel_remember_token_user ON panel_remember_token (user_id);

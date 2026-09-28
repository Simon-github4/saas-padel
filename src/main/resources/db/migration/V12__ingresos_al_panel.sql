-- Bitacora de ingresos al panel del club: quien entro, cuando, desde que IP y
-- con que dispositivo. Tambien los intentos fallidos contra un usuario que
-- existe, que son los que interesan si alguien esta probando claves.
--
-- Solo el login real con el formulario. Quien deja la pestaña abierta sigue
-- con la misma sesion dias enteros y no vuelve a aparecer hasta que la cierra.
CREATE TABLE panel_login (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Nulo para los usuarios de plataforma (soporte), igual que en club_user.
    club_id     UUID         REFERENCES tenant (id) ON DELETE CASCADE,
    -- Si se borra el usuario queda el ingreso con el nombre que tenia.
    user_id     UUID         REFERENCES club_user (id) ON DELETE SET NULL,
    user_name   VARCHAR(120) NOT NULL,
    result      VARCHAR(20)  NOT NULL
                CHECK (result IN ('OK', 'CLAVE_INCORRECTA', 'BLOQUEADO', 'DESHABILITADO')),
    ip          VARCHAR(45),
    -- mobile o desktop, igual que page_event.
    device      VARCHAR(10),
    user_agent  VARCHAR(300),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_panel_login_club_time ON panel_login (club_id, created_at);
CREATE INDEX ix_panel_login_user ON panel_login (user_id);
CREATE INDEX ix_panel_login_time ON panel_login (created_at);

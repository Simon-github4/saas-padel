-- Avisos al celular de quien atiende cuando entra una reserva por la pagina (Web
-- Push). Cada fila es un navegador de un usuario del panel que dijo "Avisarme en
-- este celular".
--
-- No depende de la sesion: el aviso tiene que llegar con el panel cerrado. Se
-- borra cuando la persona lo apaga, sale desde ese dispositivo, le cambian la
-- clave, la deshabilitan, o el servicio de push contesta que ya no existe.
--
-- endpoint, p256dh y auth son lo que entrega el navegador (PushSubscription): la
-- URL del servicio de push a la que se le manda, y las claves con las que se
-- cifra el contenido para que solo ese navegador lo lea.
--
-- V15 queda libre a proposito: la usa una rama que todavia no entro.

CREATE TABLE push_subscription (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id       UUID          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    club_user_id  UUID          NOT NULL REFERENCES club_user (id) ON DELETE CASCADE,
    endpoint      VARCHAR(1024) NOT NULL,
    p256dh        VARCHAR(120)  NOT NULL,
    auth          VARCHAR(40)   NOT NULL,
    user_agent    VARCHAR(300),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ux_push_subscription_endpoint UNIQUE (endpoint)
);
CREATE INDEX ix_push_subscription_club ON push_subscription (club_id);
CREATE INDEX ix_push_subscription_user ON push_subscription (club_user_id);

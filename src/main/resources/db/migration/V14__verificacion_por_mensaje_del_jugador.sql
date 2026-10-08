-- La verificacion del telefono se da vuelta: ya no le mandamos un codigo al
-- jugador, es el jugador el que nos escribe por WhatsApp con el codigo que le
-- mostramos en la pagina. Meta no aprueba la plantilla de autenticacion que hacia
-- falta para mandarlo nosotros, y que escriba el jugador no necesita ninguna.
--
-- Lo que prueba que el numero es suyo ya no es conocer el codigo sino desde donde
-- llega el mensaje: WhatsApp nos dice el numero que lo mando. El codigo solo ata
-- ese mensaje al pedido de la pagina, asi que se guarda tal cual -- hace falta
-- buscarlo por su valor y volver a mostrarlo si el jugador toca el boton de nuevo
-- -- y deja de haber intentos que contar: nadie lo tipea.

-- Los pedidos en curso vencen en minutos; no vale la pena convertirlos.
DELETE FROM phone_verification;

ALTER TABLE phone_verification DROP COLUMN code_hash;
ALTER TABLE phone_verification DROP COLUMN attempts;
ALTER TABLE phone_verification ADD COLUMN code VARCHAR(6) NOT NULL;
-- Cuando llego el mensaje que lo confirma. Mientras sea nulo, esta pendiente.
ALTER TABLE phone_verification ADD COLUMN confirmed_at TIMESTAMPTZ;

-- El mensaje trae el codigo, no el id: se busca por ahi.
CREATE INDEX ix_phone_verification_code ON phone_verification (code, expires_at);

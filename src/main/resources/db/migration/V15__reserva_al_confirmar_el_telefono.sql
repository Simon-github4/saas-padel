-- La reserva que espera la verificacion del telefono queda guardada en el pedido,
-- y sale en el servidor apenas llega el WhatsApp.
--
-- Hasta ahora la pagina esperaba el mensaje y reservaba ella al enterarse. Desde el
-- navegador de Instagram -- de donde llega buena parte de los jugadores -- el
-- jugador va a WhatsApp, vuelve, y la pagina que esperaba ya no existe: el numero
-- quedaba confirmado y la reserva sin hacer. Con el turno guardado aca no importa
-- si la pagina sigue abierta.
--
-- Todo nulo: un pedido sin turno es de una pagina vieja, que sigue reservando sola.

-- El turno elegido. La cancha sin clave foranea, como page_event.booking_id: el id
-- lo manda el navegador, y uno inventado tiene que terminar en una reserva que no
-- sale, no en un error al pedir el codigo.
ALTER TABLE phone_verification ADD COLUMN club_id UUID REFERENCES tenant (id) ON DELETE CASCADE;
ALTER TABLE phone_verification ADD COLUMN court_id UUID;
ALTER TABLE phone_verification ADD COLUMN start_time TIMESTAMPTZ;
ALTER TABLE phone_verification ADD COLUMN full_name VARCHAR(120);
-- La cuenta con la que reservaba, si tenia sesion: el turno le aparece en "mis turnos".
ALTER TABLE phone_verification
    ADD COLUMN player_account_id UUID REFERENCES player_account (id) ON DELETE SET NULL;

-- Lo que salio al llegar el mensaje: la reserva, o por que no se pudo, con el mismo
-- codigo y motivo que devolveria la API (ApiExceptionHandler) para que la pagina lo
-- trate igual.
ALTER TABLE phone_verification ADD COLUMN booking_id UUID REFERENCES booking (id) ON DELETE SET NULL;
ALTER TABLE phone_verification ADD COLUMN booking_error_code VARCHAR(40);
ALTER TABLE phone_verification ADD COLUMN booking_error_reason VARCHAR(40);
ALTER TABLE phone_verification ADD COLUMN booking_error_message VARCHAR(300);

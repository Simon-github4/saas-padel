-- =====================================================================
-- La reserva guarda de que cuenta de jugador salio.
--
-- Antes, el historial de /account cruzaba clubes emparejando el telefono
-- de la cuenta con el del cliente de cada reserva. El telefono no lo
-- verifica nadie: el alta lo acepta tal cual y solo se confirma el email.
-- Alcanzaba con registrarse poniendo el telefono de otro para recibir
-- sus turnos en todos los clubes -- y la consulta devolvia ademas el
-- management_token de cada uno, que es la credencial con la que se
-- cancela. Un telefono ajeno, que es un dato semipublico, valia por la
-- agenda de esa persona.
--
-- El telefono nunca fue una identidad: es un dato de contacto que
-- cualquiera puede escribir. La identidad es la cuenta, y ahora la
-- reserva la recuerda de forma explicita.
--
-- Nulo para toda reserva de invitado, que sigue siendo el camino
-- principal del producto: reservar nunca pidio cuenta. Esas se siguen
-- recuperando desde el dispositivo que las hizo (guestBookings en el
-- navegador) y por el link que recibe el jugador.
--
-- Sin rellenar las filas viejas a proposito: emparejarlas por telefono
-- seria volver a confiar, una sola vez, en el dato que este cambio deja
-- de creer. Son reservas de prueba; no hay historial real que preservar.
-- =====================================================================

ALTER TABLE booking
    ADD COLUMN player_account_id UUID REFERENCES player_account (id) ON DELETE SET NULL;

-- El historial del jugador entra por esta columna, cruzando todos los clubes.
CREATE INDEX ix_booking_player_account ON booking (player_account_id, start_time DESC);

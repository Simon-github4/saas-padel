-- La anotacion en la lista de espera recuerda la cuenta de quien se anoto, igual
-- que V4 hizo con la reserva: es lo que deja a "Mis turnos" listar las
-- anotaciones del jugador en todos los clubes y darse de baja de una. Hasta
-- ahora solo guardaba el mail congelado (V6), que no sirve como identidad: si
-- el jugador cambia de mail, sus anotaciones dejarian de ser suyas.
--
-- ON DELETE SET NULL, mismo criterio que booking.player_account_id: si la cuenta
-- se borra, la anotacion sigue y el aviso igual puede salir.
ALTER TABLE waitlist_entry
    ADD COLUMN player_account_id UUID REFERENCES player_account (id) ON DELETE SET NULL;

-- Las anotaciones que ya existen se atan por el mail con el que se anotaron:
-- anotarse exige sesion desde V6, y ese mail salio de la cuenta en ese momento.
UPDATE waitlist_entry w
SET player_account_id = a.id
FROM player_account a
WHERE w.player_account_id IS NULL
  AND w.email = a.email;

CREATE INDEX ix_waitlist_player_account ON waitlist_entry (player_account_id, starts_at);

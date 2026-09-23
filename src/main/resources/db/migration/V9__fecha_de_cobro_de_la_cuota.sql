-- Fecha de cobro de cada cuota, aparte de su periodo.
--
-- Hasta aca el cobro era "el momento en que se cargo" (created_at) y el periodo
-- de la primera cuota arrancaba siempre el dia de la carga. Para cargar a un
-- socio que ya venia (su mes empezo el 01/09, pago el 05/09 y se carga hoy) hacen
-- falta tres fechas distintas: starts_on/ends_on dicen que cubre la cuota,
-- paid_on cuando se cobro (la caja de ese dia) y created_at cuando se registro.
--
-- Las cuotas viejas se cobraron cuando se cargaron: paid_on es el dia de
-- created_at en la zona horaria del club.

ALTER TABLE gym_membership ADD COLUMN paid_on DATE;

UPDATE gym_membership m
SET paid_on = (m.created_at AT TIME ZONE t.time_zone)::date
FROM tenant t
WHERE t.id = m.club_id;

ALTER TABLE gym_membership ALTER COLUMN paid_on SET NOT NULL;

CREATE INDEX ix_gym_membership_paid_on ON gym_membership (club_id, paid_on);

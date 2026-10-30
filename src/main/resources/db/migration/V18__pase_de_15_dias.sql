-- Pase de 15 dias en el gimnasio.
--
-- Hasta aca toda cuota era mensual y seguia el ciclo del socio (su dia de corte). El
-- pase de 15 dias es para el que viene de paso: vale 15 dias calendario desde que
-- arranca (contando el primero y el ultimo) y cuando vence no genera deuda.
--
-- Alcanza con guardar el tipo de cada cuota: el regimen del socio (mensual o de
-- pases) sale de su ultima cuota paga, y el ciclo mensual sigue usando el ancla de
-- gym_member. No se puede deducir de la duracion porque las cuotas de antes de V5
-- tienen cualquier rango. Todo lo existente queda como mensual.

ALTER TABLE gym_membership
    ADD COLUMN plan_length VARCHAR(20) NOT NULL DEFAULT 'MONTH';
ALTER TABLE gym_membership
    ADD CONSTRAINT ck_gym_membership_plan_length CHECK (plan_length IN ('MONTH', 'FIFTEEN_DAYS'));
-- Un pase dura exactamente 15 dias, del primero al ultimo inclusive.
ALTER TABLE gym_membership
    ADD CONSTRAINT ck_gym_membership_pass_days CHECK (plan_length <> 'FIFTEEN_DAYS' OR ends_on = starts_on + 14);

-- La tarifa ahora es por tipo de cuota y dias por semana: la de 15 dias se fija aparte.
ALTER TABLE gym_tariff
    ADD COLUMN plan_length VARCHAR(20) NOT NULL DEFAULT 'MONTH';
ALTER TABLE gym_tariff
    ADD CONSTRAINT ck_gym_tariff_plan_length CHECK (plan_length IN ('MONTH', 'FIFTEEN_DAYS'));
ALTER TABLE gym_tariff DROP CONSTRAINT ux_gym_tariff_days;
ALTER TABLE gym_tariff
    ADD CONSTRAINT ux_gym_tariff_plan UNIQUE (club_id, plan_length, days_per_week);

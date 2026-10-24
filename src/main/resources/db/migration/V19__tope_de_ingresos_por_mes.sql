-- Tope de ingresos del gimnasio por semana o por mes.
--
-- Hasta aca el tope era siempre semanal: el socio de "2 dias por semana" entraba dos
-- veces entre el lunes y el domingo. Un club puede preferir contarlo por mes: los
-- mismos ingresos, repartidos como quiera el socio dentro del periodo de su cuota
-- (del 15 al 14 si arranco el 15). El tope del mes es proporcional a los dias del
-- periodo: dias por semana x dias del periodo / 7, redondeado (2 dias en un periodo
-- de 31 dan 9; en uno de 28, 8).
--
-- Alcanza con guardar como cuenta el club: los ingresos ya guardan su fecha y las
-- cuotas sus dias por semana, asi que cambiar de modo no toca ningun dato. Todos los
-- clubes siguen con el tope semanal.

ALTER TABLE gym_club_config
    ADD COLUMN entry_limit VARCHAR(10) NOT NULL DEFAULT 'WEEK';
ALTER TABLE gym_club_config
    ADD CONSTRAINT ck_gym_club_config_entry_limit CHECK (entry_limit IN ('WEEK', 'MONTH'));

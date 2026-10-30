-- Tipo de suspension: comun o de torneo.
--
-- Un torneo ocupa turnos puntuales de algunas canchas, no el dia entero. Para la
-- grilla es lo mismo que una suspension (la cancha no se vende en ese horario),
-- asi que no es una tabla aparte: es una suspension con otro tipo, que el panel
-- marca desde la agenda, pinta de otro color y nombra con el nombre del torneo
-- (que va en reason). Todo lo que ya estaba queda como suspension comun.

ALTER TABLE blackout
    ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'SUSPENSION';
ALTER TABLE blackout
    ADD CONSTRAINT ck_blackout_kind CHECK (kind IN ('SUSPENSION', 'TOURNAMENT'));

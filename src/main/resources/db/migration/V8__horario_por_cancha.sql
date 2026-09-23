-- Horario propio de una cancha en ciertos dias de la semana.
--
-- Hasta ahora el horario era uno solo para todo el club. Una cancha sin luz, o
-- una que se alquila a una escuela los martes a la manana, necesita el suyo:
-- en los dias que cubre, la regla de la cancha le gana al horario del club,
-- igual que una franja de tarifa de cancha le gana a la general.
CREATE TABLE court_schedule (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id     UUID        NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    court_id    UUID        NOT NULL REFERENCES court (id) ON DELETE CASCADE,
    start_time  TIME,
    end_time    TIME,
    -- Cerrada todo el dia: sin horario, y le gana a cualquier franja de ese dia.
    closed      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Fin menor al inicio vale: cierra pasada la medianoche, como el club.
    CONSTRAINT ck_court_schedule_hours CHECK (
        closed OR (start_time IS NOT NULL AND end_time IS NOT NULL AND end_time <> start_time))
);
CREATE INDEX ix_court_schedule_club ON court_schedule (club_id);

CREATE TABLE court_schedule_day (
    court_schedule_id UUID NOT NULL REFERENCES court_schedule (id) ON DELETE CASCADE,
    day_of_week       INT  NOT NULL CHECK (day_of_week BETWEEN 1 AND 7), -- ISO-8601: 1 = lunes
    PRIMARY KEY (court_schedule_id, day_of_week)
);

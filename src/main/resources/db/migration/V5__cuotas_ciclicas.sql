-- Cuotas por ciclo mensual y deuda acumulada.
--
-- Hasta aca cada gym_membership era un periodo con fechas a mano (muchas veces
-- de varios meses). A partir de aca el cobro es una fila por mes, y el mes de
-- cada socio arranca el dia del mes en que empezo a pagar (el "ancla"). Con el
-- ancla se proyectan los periodos (del dia 15 al 14, del 31 al ultimo dia del
-- mes corto...) y lo impago se acumula como deuda. Las filas viejas siguen
-- sirviendo: el ancla se siembra con la primera cuota de cada socio.

ALTER TABLE gym_member ADD COLUMN billing_anchor DATE;

-- Los socios que ya pagaron alguna vez arrancan su ciclo el dia de su primera cuota
-- (la mas vieja no anulada). El que nunca pago no tiene ancla: la primera cuota lo fija.
UPDATE gym_member m
SET billing_anchor = (
    SELECT min(x.starts_on)
    FROM gym_membership x
    WHERE x.member_id = m.id AND x.voided_at IS NULL
)
WHERE EXISTS (
    SELECT 1 FROM gym_membership x
    WHERE x.member_id = m.id AND x.voided_at IS NULL
);

-- Tarifa por cantidad de dias por semana: la cuota se fija antes (cobrar N cuotas
-- auto-completa el monto), y el mostrador sigue pudiendo ajustarlo a mano.
--
-- Es una tabla aparte y no una columna de gym_club_config para que el panel pueda
-- listar y editar los siete valores (1..7 dias por semana) con su propio servicio,
-- igual que hace con las sedes.
CREATE TABLE gym_tariff (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id       UUID          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    days_per_week INT           NOT NULL CHECK (days_per_week BETWEEN 1 AND 7),
    price         NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ux_gym_tariff_days UNIQUE (club_id, days_per_week)
);
-- Verificacion del telefono por WhatsApp antes de la primera reserva.
--
-- Un numero que nunca reservo en ningun club tiene que demostrar que existe y es
-- de quien reserva: le llega un codigo por WhatsApp y sin ese codigo no hay
-- turno. Los numeros que ya reservaron alguna vez no pasan por esto.

-- Codigos enviados. Se guardan todos los del ultimo dia, no solo el vigente: son
-- los que cuentan para el tope de envios por numero. Vale el mas nuevo.
CREATE TABLE phone_verification (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number  VARCHAR(25)  NOT NULL, -- normalizado a E.164
    -- Hash BCrypt del codigo, igual que el alta de cuentas: nunca el codigo en claro.
    code_hash     VARCHAR(100) NOT NULL,
    expires_at    TIMESTAMPTZ  NOT NULL,
    attempts      INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_phone_verification_phone ON phone_verification (phone_number, expires_at);

-- La lista de numeros que ya probaron ser reales. De toda la plataforma y no
-- por club: que el numero existe no depende de donde reserve. Despues de esta
-- migracion entran solo con el codigo; las reservas que el club carga a mano
-- cuentan igual sin estar aca (ver PhoneVerificationRepository.isRegistered).
CREATE TABLE verified_phone (
    phone_number  VARCHAR(25)  PRIMARY KEY,
    verified_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Los jugadores que los clubes ya tienen en sus listas entran verificados:
-- decision de negocio al lanzar la verificacion, porque son los jugadores
-- reales de cada club y no tiene sentido pedirles un codigo. Menos los
-- bloqueados, que son justamente los que un club no quiere dejar reservar.
INSERT INTO verified_phone (phone_number, verified_at)
SELECT phone_number, min(created_at)
FROM customer
WHERE NOT is_blocked
GROUP BY phone_number
ON CONFLICT DO NOTHING;

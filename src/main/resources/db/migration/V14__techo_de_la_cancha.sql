-- Si la cancha es techada o al aire libre, para que el jugador pueda buscar por eso.
--
-- Las canchas que ya existen quedan al aire libre. Es la opcion que menos
-- engaña: una techada cargada como descubierta solo le falta a quien filtra por
-- techo, pero una descubierta cargada como techada le promete a alguien un
-- partido bajo la lluvia. El club corrige las suyas desde Configuracion > Canchas.
ALTER TABLE court
    ADD COLUMN roof VARCHAR(10) NOT NULL DEFAULT 'OUTDOOR'
        CHECK (roof IN ('COVERED', 'OUTDOOR'));

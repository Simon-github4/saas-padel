-- Paredes y piso de cada cancha, para que el jugador pueda buscar por eso.
--
-- En Necochea conviven canchas de blindex y de pared, y algunas de las antiguas
-- no tienen alfombra: para quien juega no son lo mismo. Las canchas que ya
-- existen quedan como blindex con alfombra, que es lo mas comun; el club corrige
-- las que no lo sean desde Configuracion > Canchas.
ALTER TABLE court
    ADD COLUMN wall VARCHAR(10) NOT NULL DEFAULT 'GLASS'
        CHECK (wall IN ('GLASS', 'WALL')),
    ADD COLUMN surface VARCHAR(10) NOT NULL DEFAULT 'CARPET'
        CHECK (surface IN ('CARPET', 'NO_CARPET'));

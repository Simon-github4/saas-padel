-- Portada "logo del club".
--
-- Muchos clubes no tienen una foto de las canchas: lo unico que suben es el logo
-- que usan de perfil en Instagram, de unos 640 px. Las portadas Base y cancha
-- partida lo estiran a pantalla completa y se ve pixelado; esta lo muestra en una
-- placa que nunca lo agranda. El CHECK de V1 no tenia nombre propio, asi que
-- Postgres le puso el de siempre: tabla_columna_check.

ALTER TABLE tenant DROP CONSTRAINT tenant_hero_variant_check;
ALTER TABLE tenant
    ADD CONSTRAINT ck_tenant_hero_variant CHECK (hero_variant IN ('CLASSIC', 'SCOREBOARD', 'COURT_SPLIT', 'LOGO'));

-- El DNI del socio pasa a ser opcional.
--
-- Los gimnasios que se pasan al sistema traen socios anotados en un cuaderno sin
-- DNI: se cargan con el nombre y el DNI se completa despues. Sin DNI el socio no
-- puede entrar a la app (se entra con el DNI), pero el mostrador le cobra y le
-- registra los ingresos igual.
--
-- La constraint de formato y la de unicidad por club siguen valiendo: en
-- Postgres un NULL pasa el CHECK y no choca con otros NULL en el UNIQUE, asi que
-- puede haber varios socios sin DNI en el mismo club.

ALTER TABLE gym_member ALTER COLUMN dni DROP NOT NULL;

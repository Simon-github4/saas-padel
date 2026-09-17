-- El panel ahora deja entrar con el nombre de usuario ademas del mail (ver
-- ClubUserDetailsService), asi que el nombre necesita ser unico en toda la
-- plataforma igual que el mail: el login ocurre antes de que exista un club
-- en contexto, no hay como desambiguar por club.
CREATE UNIQUE INDEX ux_club_user_full_name ON club_user (lower(full_name));

-- Instagram del club, para mostrarlo en la pagina de reservas.
--
-- Se guarda el usuario pelado (sin @ ni link), en minusculas: el dueño lo
-- carga como le salga -"@clubpadel", "clubpadel" o el link de "Compartir
-- perfil" con su ?igsh=...- y Tenant.instagramUrl() arma siempre el mismo link
-- a partir de esto (ver InstagramHandles). 30 es el largo maximo de un usuario
-- de Instagram. Nulo para los clubes que no lo cargaron.
ALTER TABLE tenant ADD COLUMN instagram_handle VARCHAR(30);

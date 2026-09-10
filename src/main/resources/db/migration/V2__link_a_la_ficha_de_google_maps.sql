-- Antes, "Abrir en Google Maps" armaba el link solo con latitud y longitud
-- (o con direccion y ciudad, sin ninguna de las dos): eso abre un pin pelado
-- en el medio del mapa, sin nombre, fotos ni reseñas del lugar. El link que
-- el club pega en el panel -el de "Compartir" de Google Maps- ya apunta a la
-- ficha real del negocio, y GoogleMapsLinkResolver la guarda aca cuando la
-- puede identificar. Nulo para todo club que cargo su ubicacion antes de
-- este cambio, o cuyo link no traia como identificar el lugar (solo el
-- centro del mapa, sin nombre ni pin puntual): ahi Tenant.mapsUrl() sigue
-- cayendo al link armado con las coordenadas, como siempre.
ALTER TABLE tenant ADD COLUMN google_maps_url VARCHAR(500);

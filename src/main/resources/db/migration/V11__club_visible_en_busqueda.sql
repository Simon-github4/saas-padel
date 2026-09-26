-- Si el club aparece en la busqueda general de clubes (/buscar) y en el sitemap.
--
-- Un club nuevo arranca oculto mientras termina de configurarse, y se marca
-- visible a mano cuando esta listo:
--   UPDATE tenant SET listed_in_search = TRUE WHERE slug = '...';
-- Su propia pagina (/club/<slug>) funciona igual con el club oculto.
ALTER TABLE tenant ADD COLUMN listed_in_search BOOLEAN NOT NULL DEFAULT FALSE;

-- Los clubes que ya estaban siguen como estaban: visibles todos, menos el de
-- demostracion de la landing, que solo se ve desde su link.
UPDATE tenant SET listed_in_search = TRUE WHERE slug <> 'simon';

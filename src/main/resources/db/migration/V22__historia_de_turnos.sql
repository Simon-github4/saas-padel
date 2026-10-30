-- Historia de Instagram con los turnos libres del dia.
--
-- Los clubes suben todos los dias una historia con su plantilla de fondo (foto de
-- la cancha, logo, "Turnos disponibles") y los horarios libres escritos encima. El
-- panel la arma sola a partir de la agenda: la plantilla se guarda aparte, igual
-- que la foto de portada (tenant se carga en casi cualquier request y ninguno
-- necesita la imagen), y en tenant queda solo donde y como se escriben los
-- horarios sobre ella.

CREATE TABLE tenant_story_template (
    tenant_id     UUID PRIMARY KEY REFERENCES tenant (id) ON DELETE CASCADE,
    data          BYTEA        NOT NULL,
    content_type  VARCHAR(100) NOT NULL,
    -- Version de la plantilla: el panel reusa la que ya bajo mientras no cambie.
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- La franja de la plantilla donde van los horarios, en porcentaje del alto: cada
-- plantilla deja libre una zona distinta (en la de ejemplo, entre el titulo y la red).
ALTER TABLE tenant
    ADD COLUMN story_area_top INT NOT NULL DEFAULT 28,
    ADD COLUMN story_area_bottom INT NOT NULL DEFAULT 82,
    ADD COLUMN story_style VARCHAR(10) NOT NULL DEFAULT 'PILLS',
    ADD COLUMN story_title BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tenant
    ADD CONSTRAINT ck_tenant_story_area CHECK (
        story_area_top BETWEEN 0 AND 100
        AND story_area_bottom BETWEEN 0 AND 100
        AND story_area_bottom - story_area_top >= 20);
ALTER TABLE tenant
    ADD CONSTRAINT ck_tenant_story_style CHECK (story_style IN ('PILLS', 'COLOR', 'LIGHT', 'DARK'));

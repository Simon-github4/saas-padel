-- Pedidos de buffet sin turno.
--
-- Hasta ahora todo producto vendido y todo cobro colgaba de un turno
-- (booking_id NOT NULL en product_sale y en payment). Pero el buffet tambien le
-- vende a quien no juega: el que viene a mirar, el acompañante. Un pedido de
-- buffet es eso: un nombre -"a nombre de" quien se pidio-, los productos que se
-- le van sumando despues de abrirlo, y los cobros, igual que un turno.
CREATE TABLE buffet_order (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id       UUID          NOT NULL REFERENCES tenant (id) ON DELETE CASCADE,
    customer_name VARCHAR(120)  NOT NULL,
    total_price   NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (total_price >= 0),
    paid_amount   NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (paid_amount >= 0),
    registered_by UUID          REFERENCES club_user (id) ON DELETE SET NULL,
    version       BIGINT        NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
-- La caja lista los pedidos del dia por fecha de creacion.
CREATE INDEX ix_buffet_order_club_created ON buffet_order (club_id, created_at);

-- Una venta es de un turno o de un pedido, nunca de los dos ni de ninguno.
ALTER TABLE product_sale ALTER COLUMN booking_id DROP NOT NULL;
ALTER TABLE product_sale
    ADD COLUMN buffet_order_id UUID REFERENCES buffet_order (id) ON DELETE CASCADE;
ALTER TABLE product_sale ADD CONSTRAINT ck_product_sale_owner
    CHECK ((booking_id IS NULL) <> (buffet_order_id IS NULL));
CREATE INDEX ix_product_sale_buffet_order ON product_sale (buffet_order_id);

-- Lo mismo para los cobros.
ALTER TABLE payment ALTER COLUMN booking_id DROP NOT NULL;
ALTER TABLE payment
    ADD COLUMN buffet_order_id UUID REFERENCES buffet_order (id) ON DELETE CASCADE;
ALTER TABLE payment ADD CONSTRAINT ck_payment_owner
    CHECK ((booking_id IS NULL) <> (buffet_order_id IS NULL));
CREATE INDEX ix_payment_buffet_order ON payment (buffet_order_id);

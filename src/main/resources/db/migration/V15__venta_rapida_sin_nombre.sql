-- Una venta de buffet que se cobra en el momento no necesita nombre: en un
-- torneo, el que compra un agua y paga no se anota. El nombre sigue siendo
-- obligatorio para dejar algo en la cuenta, y eso lo controla la aplicacion.
ALTER TABLE buffet_order ALTER COLUMN customer_name DROP NOT NULL;

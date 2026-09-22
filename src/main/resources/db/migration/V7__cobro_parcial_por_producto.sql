-- Cobro parcial de un pedido de buffet: qué línea ya se cobró.
--
-- El saldo de un pedido siempre fue a nivel de la cuenta entera (total menos
-- pagado), sin saber qué producto puntual cubrió cada cobro. Al tildar
-- productos para cobrar solo esos, hacía falta acordarse cuáles ya se
-- cobraron: si no, al reabrir el pedido aparecían todos destildados de nuevo
-- aunque el saldo estuviera bien.
ALTER TABLE product_sale ADD COLUMN paid boolean NOT NULL DEFAULT false;

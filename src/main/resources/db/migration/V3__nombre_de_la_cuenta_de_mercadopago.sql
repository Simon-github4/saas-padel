-- Configuracion mostraba la cuenta de MercadoPago conectada como "cuenta
-- #123456789": el user_id, que es lo unico que trae el intercambio OAuth. El
-- dueno no reconoce ese numero, y no tiene como saber si conecto su cuenta o
-- la de otro. Nombre y email los pide la aplicacion a MercadoPago (/users/me)
-- al conectar, para mostrar algo que el dueno si reconozca.
--
-- Nulos para las conexiones anteriores a esto: se completan la primera vez
-- que el dueno abre la pestana de cobros (MercadoPagoOAuthService.loadMissingAccount).
ALTER TABLE tenant
    ADD COLUMN mp_account_name  VARCHAR(160),
    ADD COLUMN mp_account_email VARCHAR(255);

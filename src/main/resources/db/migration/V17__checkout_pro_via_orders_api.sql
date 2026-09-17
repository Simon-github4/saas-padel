-- El pago ya no arranca con una preferencia (Preferences API) sino con una
-- order de la API de Orders: la columna se renombra para no mentir sobre que
-- identificador guarda.
ALTER TABLE payment RENAME COLUMN mp_preference_id TO mp_order_id;
ALTER INDEX ix_payment_preference RENAME TO ix_payment_order;

-- Cuando un jugador cancela por la web un turno que tenia gente anotada en la
-- lista de espera, el panel levanta una alerta (AlertService.waitlistSlotFreed)
-- para que el club revise los anotados y les escriba por WhatsApp. El CHECK de
-- V1 enumera los tipos a mano, asi que hay que rehacerlo con el nuevo.
--
-- El nombre es el que Postgres le puso solo al CHECK sin nombre de la columna
-- (<tabla>_<columna>_check).
ALTER TABLE operational_alert DROP CONSTRAINT operational_alert_type_check;
ALTER TABLE operational_alert ADD CONSTRAINT operational_alert_type_check CHECK (type IN
    ('REFUND_REQUIRED', 'ORPHAN_PAYMENT', 'NOTIFICATION_FAILED', 'RECURRING_CONFLICT',
     'WAITLIST_SLOT_FREED'));

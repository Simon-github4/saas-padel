-- El tope de turnos futuros por telefono pasa de 3 a 7 por defecto, en linea
-- con Tenant.maxActiveBookings.
--
-- Solo cambia el default para los clubes que se den de alta desde ahora: a
-- los que ya existen no se les toca el valor, porque desde la base no hay
-- forma de distinguir un 3 heredado de un 3 que el club eligio a proposito
-- en Ajustes.
ALTER TABLE tenant ALTER COLUMN max_active_bookings SET DEFAULT 7;

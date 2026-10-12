import { useEffect, useState } from 'react';
import { ApiError, api, type BookingDetail } from './api/client';
import { usePlayerAuth } from './auth/AuthContext';
import {
  readGuestBookings,
  updateGuestBookings,
  type GuestBooking,
  type GuestBookingUpdate,
} from './guestBookings';

/**
 * Qué hacer con un turno guardado según lo que respondió su link: sus datos al
 * día, null si el link ya no existe, o undefined si no se pudo saber.
 *
 * <p>Solo lo borra un 404 de nuestra API, con su código NOT_FOUND. Un error de
 * red o del servidor no dice nada del turno, y tampoco un 404 sin ese código,
 * que puede ser la página de error de un proxy en medio de un deploy: borrarlo
 * por eso sería dejar al jugador sin la única forma de volver a abrirlo.
 */
export function guestUpdate(result: PromiseSettledResult<BookingDetail>): GuestBookingUpdate | undefined {
  if (result.status === 'fulfilled') {
    const { status, clubName, courtName, startTime, endTime } = result.value;
    return { status, clubName, courtName, startTime, endTime };
  }
  const gone =
    result.reason instanceof ApiError && result.reason.status === 404 && result.reason.code === 'NOT_FOUND';
  return gone ? null : undefined;
}

/**
 * Los turnos reservados sin cuenta en este dispositivo, puestos al día con el
 * servidor cada vez que se monta la pantalla que los usa (al entrar o al
 * recargar).
 *
 * <p>Arranca con lo guardado, con el último estado conocido, y lo reemplaza
 * cuando llegan las respuestas: una consulta por turno, a su mismo link de
 * gestión. Son pocos, porque la lista se poda sola una hora después de cada
 * turno. {@code checked} dice si ya llegaron: quien anuncie un turno como "tu
 * próximo" tiene que esperarlas, o podría anunciar uno que ya no existe.
 *
 * <p>Se vuelve a leer al cambiar la sesión: al entrar, los turnos de este
 * dispositivo pasan a la cuenta y se borran de acá (ver claimGuestBookings).
 */
export function useGuestBookings(): { bookings: GuestBooking[]; checked: boolean } {
  const { session } = usePlayerAuth();
  const token = session?.token;
  const [state, setState] = useState(() => {
    const bookings = readGuestBookings();
    return { bookings, checked: bookings.length === 0 };
  });

  useEffect(() => {
    const stored = readGuestBookings();
    setState({ bookings: stored, checked: stored.length === 0 });
    if (stored.length === 0) {
      return;
    }
    let cancelled = false;
    void Promise.allSettled(stored.map((booking) => api.booking(booking.managementToken))).then(
      (results) => {
        const updates = new Map<string, GuestBookingUpdate>();
        results.forEach((result, index) => {
          const update = guestUpdate(result);
          if (update !== undefined) {
            updates.set(stored[index].bookingId, update);
          }
        });
        // Se guarda aunque la pantalla ya se haya ido: lo que respondió el
        // servidor sigue siendo cierto, y la próxima arranca al día.
        updateGuestBookings(updates);
        if (!cancelled) {
          setState({ bookings: readGuestBookings(), checked: true });
        }
      },
    );
    return () => {
      cancelled = true;
    };
  }, [token]);

  return state;
}

/**
 * Turnos reservados sin cuenta, recordados en este navegador.
 *
 * <p>Es la única forma de volver a {@code /manage/:token} que le queda a quien
 * reservó sin loguearse y salió de la pantalla de éxito sin guardar el link:
 * sin esto no tiene cómo cancelar. No reemplaza a la cuenta -- solo funciona en
 * este mismo dispositivo -- pero cubre el caso más común, volver en un rato
 * desde el mismo celular.
 */

const STORAGE_KEY = 'padel_guest_bookings';
const MAX_ENTRIES = 20;

export interface GuestBooking {
  bookingId: string;
  managementToken: string;
  clubName: string;
  clubSlug: string;
  courtName: string;
  startTime: string;
}

export function readGuestBookings(): GuestBooking[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return [];
    }
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function rememberGuestBooking(booking: GuestBooking): void {
  try {
    const list = readGuestBookings().filter((existing) => existing.bookingId !== booking.bookingId);
    list.unshift(booking);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(list.slice(0, MAX_ENTRIES)));
  } catch {
    // Sin localStorage (privado, cuota llena) el turno sigue reservado igual;
    // el jugador solo se queda sin el atajo para volver.
  }
}

/** Se llama al cancelar, para que la lista no acumule turnos que ya no sirven. */
export function forgetGuestBooking(bookingId: string): void {
  try {
    const list = readGuestBookings().filter((existing) => existing.bookingId !== bookingId);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(list));
  } catch {
    // no-op
  }
}

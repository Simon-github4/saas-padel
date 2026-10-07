/**
 * Nombre y teléfono de la última reserva sin cuenta, recordados en este navegador.
 *
 * <p>Es comodidad, no identidad: el checkout lo ofrece como sugerencia y nada
 * más. Lo que diga localStorage no prueba nada, y si un número necesita
 * verificarse lo decide el servidor, venga de donde venga.
 *
 * <p>Se ofrece y no se precarga porque el dispositivo puede no ser de quien
 * reserva: el celular de un amigo, la tablet del mostrador. Con los campos ya
 * llenos, el siguiente reservaría a nombre del anterior sin darse cuenta.
 *
 * <p>Se guarda al reservar y no al escribir: así queda un número con el que ya
 * se reservó, no uno mal escrito a medio tipear.
 */

const STORAGE_KEY = 'padel_guest_contact';

/**
 * Cuánto se recuerda sin volver a reservar. Cada reserva lo renueva, así que
 * quien juega seguido no lo pierde nunca; el que no vuelve deja de tener sus
 * datos guardados en un navegador que quizás ni es suyo.
 */
const TTL_MS = 180 * 24 * 60 * 60 * 1000;

export interface GuestContact {
  fullName: string;
  /** Como lo escribió el jugador ("2262 21-2345"): el servidor lo entiende igual. */
  phone: string;
}

interface Stored extends GuestContact {
  savedAt: number;
}

/** El contacto guardado, o null si no hay, venció o está ilegible (y en ese caso se borra). */
export function readGuestContact(now: number = Date.now()): GuestContact | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as Partial<Stored>;
    const valid =
      typeof parsed.fullName === 'string' &&
      typeof parsed.phone === 'string' &&
      typeof parsed.savedAt === 'number' &&
      parsed.fullName.trim() !== '' &&
      parsed.phone.trim() !== '' &&
      now < parsed.savedAt + TTL_MS;
    if (!valid) {
      localStorage.removeItem(STORAGE_KEY);
      return null;
    }
    return { fullName: parsed.fullName!, phone: parsed.phone! };
  } catch {
    return null;
  }
}

export function rememberGuestContact(contact: GuestContact, now: number = Date.now()): void {
  const fullName = contact.fullName.trim();
  const phone = contact.phone.trim();
  if (!fullName || !phone) {
    return;
  }
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ fullName, phone, savedAt: now } satisfies Stored));
  } catch {
    // Sin localStorage (privado, cuota llena) la reserva sale igual; la próxima
    // vez solo hay que escribir los datos de nuevo.
  }
}

/** "No soy yo": el jugador pide que este navegador lo olvide. */
export function forgetGuestContact(): void {
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch {
    // no-op
  }
}

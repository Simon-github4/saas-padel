/**
 * Turnos reservados sin cuenta, recordados en este navegador.
 *
 * <p>Es la única forma de volver a {@code /manage/:token} que le queda a quien
 * reservó sin loguearse y salió de la pantalla de éxito sin guardar el link:
 * sin esto no tiene cómo cancelar. No reemplaza a la cuenta -- solo funciona en
 * este mismo dispositivo -- pero cubre el caso más común, volver en un rato
 * desde el mismo celular.
 *
 * <p>Lo que se guarda acá es el token de gestión, que es la credencial con la
 * que se cancela el turno. Por eso la lista se poda sola: ver más abajo.
 */

const STORAGE_KEY = 'padel_guest_bookings';
const MAX_ENTRIES = 20;

/**
 * Cuánto sobrevive un turno en esta lista después de terminar.
 *
 * <p>Una vez jugado el partido, el link no sirve para nada: no se cancela un
 * turno que ya pasó. Lo único que queda es una credencial guardada en el
 * navegador sin ninguna función, y una credencial que no se usa es solo
 * superficie expuesta. La hora de gracia es para el turno que se está jugando
 * o recién terminó, donde todavía puede hacer falta abrirlo.
 */
const GRACE_MS = 60 * 60 * 1000;

/**
 * Cuánto se asume que dura un turno cuando la entrada no guarda su fin.
 *
 * <p>Las entradas anteriores a este cambio solo tienen `startTime`. Cuatro horas
 * es el turno más largo que el esquema permite configurar, así que con este
 * techo ninguna se descarta mientras todavía podía servir.
 */
const LEGACY_DURATION_MS = 4 * 60 * 60 * 1000;

export interface GuestBooking {
  bookingId: string;
  managementToken: string;
  clubName: string;
  clubSlug: string;
  courtName: string;
  startTime: string;
  /** Ausente en las entradas guardadas antes de que existiera la poda. */
  endTime?: string;
  /**
   * Cómo está el turno según el servidor, la última vez que se preguntó (ver
   * {@code useGuestBookings}). Ausente hasta esa primera consulta, y en las
   * entradas guardadas antes de que existiera.
   */
  status?: string;
}

/**
 * Lo que dice hoy el servidor de un turno guardado: sus datos al día, o null si
 * su link ya no corresponde a ningún turno.
 */
export type GuestBookingUpdate = Pick<
  GuestBooking,
  'status' | 'clubName' | 'courtName' | 'startTime' | 'endTime'
> | null;

/**
 * Los turnos que este navegador recuerda, ya sin los que vencieron.
 *
 * <p>La poda ocurre acá y no en un trabajo aparte porque no hay dónde correrlo:
 * es el navegador del jugador. Toda lectura la deja al día, y toda escritura
 * pasa antes por una lectura.
 */
export function readGuestBookings(): GuestBooking[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return [];
    }
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) {
      return [];
    }
    // El ahora se calcula una vez y se pasa explícito. Nada de
    // `.filter(sigueSirviendo)`: filter le pasa (elemento, índice, array) a la
    // función, así que el índice terminaría ocupando el lugar del instante.
    const ahora = Date.now();
    const vigentes = (parsed as GuestBooking[]).filter((booking) => sigueSirviendo(booking, ahora));
    if (vigentes.length !== parsed.length) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(vigentes));
    }
    return vigentes;
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

/**
 * Pone al día los turnos guardados con lo que respondió el servidor.
 *
 * <p>Sin esto la lista solo se enteraba de una baja si el jugador cancelaba desde
 * este mismo navegador. Si lo cancelaba el club, se caía por no pagar la seña o
 * por no confirmarlo, o lo cancelaba desde otro teléfono, acá seguía como si
 * nada. Un turno cuyo link ya no existe se borra; los que no vienen en
 * {@code updates} (la consulta falló, no hay conexión) quedan como estaban.
 */
export function updateGuestBookings(updates: Map<string, GuestBookingUpdate>): void {
  try {
    const list = readGuestBookings().flatMap((booking) => {
      if (!updates.has(booking.bookingId)) {
        return [booking];
      }
      const update = updates.get(booking.bookingId);
      return update ? [{ ...booking, ...update }] : [];
    });
    localStorage.setItem(STORAGE_KEY, JSON.stringify(list));
  } catch {
    // Sin localStorage no hay lista que poner al día.
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

/** Vence una hora después de terminar el turno. Una fecha ilegible se descarta. */
function sigueSirviendo(booking: GuestBooking, ahora: number): boolean {
  const fin = finDe(booking);
  return fin !== null && ahora < fin + GRACE_MS;
}

function finDe(booking: GuestBooking): number | null {
  const fin = booking.endTime ? Date.parse(booking.endTime) : Number.NaN;
  if (!Number.isNaN(fin)) {
    return fin;
  }
  const inicio = Date.parse(booking.startTime);
  return Number.isNaN(inicio) ? null : inicio + LEGACY_DURATION_MS;
}

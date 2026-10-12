import type { BookingHistoryItem, RecurringItem } from './api/client';
import { formatHours, longDate } from './format';

/**
 * Los estados de un turno que, si su horario no terminó, todavía se va a jugar.
 *
 * <p>COMPLETED también: el club lo pone solo al cobrar el total en el mostrador,
 * muchas veces antes del partido. Lo que dice si ya se jugó es la hora.
 */
const ACTIVE = new Set(['CONFIRMED', 'COMPLETED', 'AWAITING_CONFIRMATION', 'DRAFT']);

/**
 * La fecha de esta semana de un turno fijo, para listarla junto a los demás
 * turnos. No es un {@link BookingHistoryItem}: no tiene token, así que no se
 * puede abrir (ver {@code RecurringItem}).
 */
export interface FixedOccurrence {
  recurringId: string;
  clubName: string;
  clubSlug: string;
  courtName: string;
  startTime: string;
  endTime: string;
  status: string;
}

/** Lo que se lista en "Mis turnos": un turno de la cuenta o la fecha de un fijo. */
export type AccountBooking = BookingHistoryItem | FixedOccurrence;

export function isFixed(item: AccountBooking): item is FixedOccurrence {
  return 'recurringId' in item;
}

/** Cuántos días hacia adelante entra la fecha de un turno fijo a la lista, hoy incluido. */
const FIXED_WINDOW_DAYS = 7;

/**
 * Las fechas de los turnos fijos que caen en los próximos siete días.
 *
 * <p>Solo la próxima de cada fijo, y solo si es de esta semana: las de más
 * adelante ya las dice la sección de turnos fijos, y listarlas todas llenaría
 * "Próximos" de la misma tarjeta repetida.
 */
export function fixedThisWeek(recurring: RecurringItem[], now: Date): FixedOccurrence[] {
  const windowEnd = new Date(now.getFullYear(), now.getMonth(), now.getDate() + FIXED_WINDOW_DAYS);
  return recurring.flatMap((fixed) => {
    const next = fixed.next;
    if (!next || new Date(next.endTime) <= now || new Date(next.startTime) >= windowEnd) {
      return [];
    }
    return [
      {
        recurringId: fixed.recurringId,
        clubName: fixed.clubName,
        clubSlug: fixed.clubSlug,
        courtName: fixed.courtName,
        startTime: next.startTime,
        endTime: next.endTime,
        status: next.status,
      },
    ];
  });
}

/** El mínimo que hace falta para ubicar un turno en el tiempo. */
type Timed = { startTime: string; endTime: string; status: string };

/** Del más cercano al más lejano. */
function byStart(a: Timed, b: Timed): number {
  return new Date(a.startTime).getTime() - new Date(b.startTime).getTime();
}

/**
 * Reparte el historial en tres: lo de hoy, lo que viene y lo que ya pasó.
 *
 * <ul>
 *   <li>{@code today}: los que se juegan hoy y todavía no terminaron. Uno que
 *       ya empezó sigue acá: el jugador está en la cancha, no es historia todavía.
 *   <li>{@code upcoming}: el resto de lo que no terminó -- los de mañana en
 *       adelante, y también los cancelados de fechas que todavía no llegaron, que
 *       no se van a jugar pero tampoco son "anteriores".
 *   <li>{@code past}: lo que ya terminó, sea cual sea su estado.
 * </ul>
 *
 * <p>Los dos primeros van del más cercano al más lejano; los anteriores
 * conservan el orden en que llega el historial, del más nuevo al más viejo.
 */
export function splitBookings<T extends Timed>(
  history: T[],
  now: Date,
): { today: T[]; upcoming: T[]; past: T[] } {
  const tomorrow = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
  const today: T[] = [];
  const upcoming: T[] = [];
  const past: T[] = [];
  for (const item of history) {
    if (new Date(item.endTime) <= now) {
      past.push(item);
    } else if (ACTIVE.has(item.status) && new Date(item.startTime) < tomorrow) {
      today.push(item);
    } else {
      upcoming.push(item);
    }
  }
  today.sort(byStart);
  upcoming.sort(byStart);
  return { today, upcoming, past };
}

/**
 * Cuánto falta para un turno de hoy: "En 40 min", "En 2 h 30 min", o "Ahora"
 * si ya empezó. Que es hoy ya lo dice la sección; esto es lo que el jugador
 * quiere saber además.
 */
export function whenLabel(item: Timed, now: Date): string {
  const minutes = Math.ceil((new Date(item.startTime).getTime() - now.getTime()) / 60_000);
  return minutes <= 0 ? 'Ahora' : `En ${formatHours(minutes)}`;
}

/**
 * El turno que viene y cuántos más hay después, para el aviso de arriba del
 * buscador.
 *
 * <p>Solo cuentan los que se van a jugar. Un cancelado de una fecha que no llegó
 * sigue en "Próximos" de la cuenta, pero anunciarlo como "tu próximo turno"
 * sería mandar al jugador a una cancha que ya no tiene. Uno que ya empezó sí
 * cuenta, como en Hoy: el jugador está en la cancha.
 */
export function nextBooking<T extends Timed>(items: T[], now: Date): { next: T | null; more: number } {
  const pending = items
    .filter((item) => ACTIVE.has(item.status) && new Date(item.endTime) > now)
    .sort(byStart);
  return { next: pending[0] ?? null, more: Math.max(pending.length - 1, 0) };
}

/** Hasta cuántas horas antes un turno de mañana lleva la cuenta regresiva además del día. */
const COUNTDOWN_HOURS = 12;

/**
 * Qué día es, dicho como lo diría el jugador: "Ahora", "Hoy, en 40 min",
 * "Mañana" o la fecha ("sábado 17 de octubre"). La hora va aparte, en grande.
 *
 * <p>Un turno de mañana que empieza en menos de doce horas dice además cuánto
 * falta: "Mañana, en 2 h 30 min". Es el de la madrugada -- 00:30 visto a las
 * 22:00 --, y "Mañana 00:30" solo se puede leer como mañana a la noche: el
 * jugador se perdería el partido de hoy a la noche.
 */
export function dayLabel(item: Timed, now: Date): string {
  const start = new Date(item.startTime);
  const tomorrow = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1);
  const dayAfter = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 2);
  if (start < tomorrow) {
    const when = whenLabel(item, now);
    return when === 'Ahora' ? when : `Hoy, ${when.toLowerCase()}`;
  }
  if (start < dayAfter) {
    const soon = start.getTime() - now.getTime() < COUNTDOWN_HOURS * 60 * 60 * 1000;
    return soon ? `Mañana, ${whenLabel(item, now).toLowerCase()}` : 'Mañana';
  }
  return longDate(item.startTime);
}

/**
 * Los estados en los que la página del turno ofrece cancelarlo, online o
 * escribiéndole al club ({@code active} en ManagePage). Tienen que coincidir: si
 * no, el botón promete "cancelar" y la página no tiene cómo. Un turno esperando
 * pago o que el club ya cobró entero no se cancela desde ahí.
 */
const CANCELLABLE = new Set(['CONFIRMED', 'AWAITING_CONFIRMATION']);

/**
 * Si tiene sentido ofrecerle cancelarlo: está en un estado que la página del
 * turno deja cancelar y todavía no empezó. Al resto solo se lo puede mirar.
 *
 * <p>No dice si el club deja cancelarlo online: eso depende de cuánto falta, y
 * lo resuelve la página del turno, que si no se puede ofrece escribirle al club.
 */
export function offersCancel(item: Timed, now: Date): boolean {
  return CANCELLABLE.has(item.status) && new Date(item.startTime) > now;
}

/** El texto del botón que abre un turno: "Ver o cancelar" si se puede cancelar, si no "Ver turno". */
export function manageLabel(item: Timed, now: Date): string {
  return offersCancel(item, now) ? 'Ver o cancelar' : 'Ver turno';
}

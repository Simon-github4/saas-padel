import type { BookingHistoryItem, RecurringItem } from './api/client';
import { formatHours } from './format';

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
  const byStart = (a: T, b: T) =>
    new Date(a.startTime).getTime() - new Date(b.startTime).getTime();
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

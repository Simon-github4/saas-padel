import type { BookingHistoryItem } from './api/client';
import { durationMinutes, longDate, monthName } from './format';

/**
 * La ventana del resumen. 'week' y 'month' son ventanas corridas que terminan
 * hoy -- los últimos 7 y 30 días -- y no la semana o el mes del calendario, que
 * el lunes o el día 1 arrancaban en cero aunque el jugador hubiera jugado ayer.
 */
export type Period = 'week' | 'month' | 'year' | 'all';

/** Cuántos días abarca cada ventana corrida, hoy incluido. */
const WINDOW_DAYS = { week: 7, month: 30 } as const;

/**
 * Una barra del gráfico: un tramo de tiempo con los turnos que caen adentro.
 *
 * <p>{@code start} es inclusivo y {@code end} exclusivo, así dos barras vecinas
 * no se pelean por el turno que arranca justo en el límite.
 */
export interface Bucket {
  /** Lo que va abajo de la barra: "L", "14", "E", "2025". Vacío si esa barra no lleva rótulo. */
  label: string;
  /** El nombre entero, para el detalle al tocar y para el lector de pantalla. */
  fullLabel: string;
  start: Date;
  end: Date;
  turnos: number;
  minutos: number;
}

/**
 * Un turno cuenta como jugado si ya terminó y no se canceló ni faltó nadie.
 *
 * <p>Que el club lo haya cerrado (COMPLETED) no alcanza: lo cierra solo al
 * cobrar el total en el mostrador, y eso pasa muchas veces antes del partido.
 */
export function isPlayed(item: BookingHistoryItem, now: Date): boolean {
  const ended = new Date(item.endTime) <= now;
  return ended && (item.status === 'COMPLETED' || item.status === 'CONFIRMED');
}

/** Medianoche desde la que arranca la ventana; null para 'all'. */
export function periodStart(period: Period, now: Date): Date | null {
  if (period === 'all') {
    return null;
  }
  if (period === 'year') {
    return new Date(now.getFullYear(), 0, 1);
  }
  return addDays(startOfDay(now), -(WINDOW_DAYS[period] - 1));
}

/**
 * Las barras del período, ya con los turnos jugados repartidos adentro.
 *
 * <p>Los límites se arman con los componentes de la fecha local y no sumando
 * milisegundos: el turno de la noche en que cambia el horario de verano tiene
 * que caer en su día, no en el de al lado.
 */
export function periodBuckets(
  history: BookingHistoryItem[],
  period: Period,
  now: Date,
): Bucket[] {
  const buckets = emptyBuckets(period, now, history);
  if (buckets.length === 0) {
    return buckets;
  }
  for (const item of history) {
    if (!isPlayed(item, now)) {
      continue;
    }
    const startedAt = new Date(item.startTime).getTime();
    const bucket = buckets.find(
      (candidate) => startedAt >= candidate.start.getTime() && startedAt < candidate.end.getTime(),
    );
    if (!bucket) {
      continue;
    }
    bucket.turnos += 1;
    bucket.minutos += durationMinutes(item.startTime, item.endTime);
  }
  return buckets;
}

/**
 * Turnos jugados dentro de la ventana.
 *
 * <p>Los totales salen de sumar las mismas barras que dibuja el gráfico y no de
 * un filtro aparte: con dos caminos de cálculo, tarde o temprano el número
 * grande y las barras terminan diciendo cosas distintas.
 */
export function summarize(
  history: BookingHistoryItem[],
  period: Period,
  now: Date,
): { turnos: number; minutos: number; buckets: Bucket[]; range: { from: Date; to: Date } | null } {
  const buckets = periodBuckets(history, period, now);
  return {
    turnos: buckets.reduce((total, item) => total + item.turnos, 0),
    minutos: buckets.reduce((total, item) => total + item.minutos, 0),
    buckets,
    range: periodRange(history, period, now),
  };
}

/**
 * De qué día a qué día va la ventana, para decirlo arriba del gráfico.
 *
 * <p>Termina siempre hoy. El histórico arranca el día del primer turno jugado,
 * no el 1 de enero de ese año, y sin nada jugado no tiene rango que mostrar.
 */
export function periodRange(
  history: BookingHistoryItem[],
  period: Period,
  now: Date,
): { from: Date; to: Date } | null {
  const start = periodStart(period, now);
  if (start) {
    return { from: start, to: startOfDay(now) };
  }
  const played = history
    .filter((item) => isPlayed(item, now))
    .map((item) => new Date(item.startTime).getTime());
  if (played.length === 0) {
    return null;
  }
  return { from: startOfDay(new Date(Math.min(...played))), to: startOfDay(now) };
}

const DAY_LABELS = ['L', 'M', 'M', 'J', 'V', 'S', 'D'];

/** Las barras vacías del período: los límites y los rótulos, todavía sin turnos. */
function emptyBuckets(period: Period, now: Date, history: BookingHistoryItem[]): Bucket[] {
  if (period === 'week' || period === 'month') {
    // Una barra por día en las dos. En la semana cada una lleva la inicial de
    // su día; en el mes, treinta iniciales no entran, así que solo los lunes
    // llevan rótulo, con el número de día, para ubicarse.
    const first = periodStart(period, now) as Date;
    return Array.from({ length: WINDOW_DAYS[period] }, (_unused, index) => {
      const start = addDays(first, index);
      const weekday = (start.getDay() + 6) % 7; // lunes = 0
      const label =
        period === 'week' ? DAY_LABELS[weekday] : weekday === 0 ? String(start.getDate()) : '';
      return newBucket(label, longDate(isoDay(start)), start, addDays(start, 1));
    });
  }

  if (period === 'year') {
    return Array.from({ length: 12 }, (_unused, month) => {
      const start = new Date(now.getFullYear(), month, 1);
      const name = monthName(start);
      const capitalized = name.charAt(0).toUpperCase() + name.slice(1);
      return newBucket(
        capitalized.charAt(0),
        capitalized,
        start,
        new Date(now.getFullYear(), month + 1, 1),
      );
    });
  }

  const years = history
    .filter((item) => isPlayed(item, now))
    .map((item) => new Date(item.startTime).getFullYear());
  if (years.length === 0) {
    return [];
  }
  const from = Math.min(...years);
  return Array.from({ length: now.getFullYear() - from + 1 }, (_unused, offset) => {
    const year = from + offset;
    return newBucket(String(year), String(year), new Date(year, 0, 1), new Date(year + 1, 0, 1));
  });
}

function newBucket(label: string, fullLabel: string, start: Date, end: Date): Bucket {
  return { label, fullLabel, start, end, turnos: 0, minutos: 0 };
}

function startOfDay(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

function addDays(date: Date, days: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days);
}

/** YYYY-MM-DD en local, que es como longDate espera una fecha sin hora. */
function isoDay(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

import type { BookingHistoryItem } from './api/client';
import { durationMinutes, longDate, monthName } from './format';

export type Period = 'week' | 'month' | 'year' | 'all';

/**
 * Una barra del gráfico: un tramo de tiempo con los turnos que caen adentro.
 *
 * <p>{@code start} es inclusivo y {@code end} exclusivo, así dos barras vecinas
 * no se pelean por el turno que arranca justo en el límite.
 */
export interface Bucket {
  /** Lo que va abajo de la barra: "L", "S1", "E", "2025". */
  label: string;
  /** El nombre entero, para el detalle al tocar y para el lector de pantalla. */
  fullLabel: string;
  start: Date;
  end: Date;
  turnos: number;
  minutos: number;
}

/** Un turno cuenta como jugado si ya terminó y no se canceló ni faltó nadie. */
export function isPlayed(item: BookingHistoryItem, now: Date): boolean {
  if (item.status === 'COMPLETED') {
    return true;
  }
  return item.status === 'CONFIRMED' && new Date(item.endTime) <= now;
}

/** Instante desde el que arranca la ventana; null para 'all'. Semana de lunes a domingo. */
export function periodStart(period: Period, now: Date): Date | null {
  if (period === 'all') {
    return null;
  }
  if (period === 'year') {
    return new Date(now.getFullYear(), 0, 1);
  }
  if (period === 'month') {
    return new Date(now.getFullYear(), now.getMonth(), 1);
  }
  const dayOffset = (now.getDay() + 6) % 7; // lunes = 0
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - dayOffset);
  return start;
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
): { turnos: number; minutos: number; buckets: Bucket[] } {
  const buckets = periodBuckets(history, period, now);
  return {
    turnos: buckets.reduce((total, item) => total + item.turnos, 0),
    minutos: buckets.reduce((total, item) => total + item.minutos, 0),
    buckets,
  };
}

const DAY_LABELS = ['L', 'M', 'M', 'J', 'V', 'S', 'D'];

/** Las barras vacías del período: los límites y los rótulos, todavía sin turnos. */
function emptyBuckets(period: Period, now: Date, history: BookingHistoryItem[]): Bucket[] {
  if (period === 'week') {
    const monday = periodStart('week', now) as Date;
    return DAY_LABELS.map((label, index) => {
      const start = addDays(monday, index);
      return newBucket(label, longDate(isoDay(start)), start, addDays(start, 1));
    });
  }

  if (period === 'month') {
    const monthEnd = new Date(now.getFullYear(), now.getMonth() + 1, 1);
    const weeks: Bucket[] = [];
    let cursor = new Date(now.getFullYear(), now.getMonth(), 1);
    while (cursor < monthEnd) {
      // El corte va por lunes, así la primera semana del mes puede ser más corta.
      const nextMonday = addDays(cursor, 7 - ((cursor.getDay() + 6) % 7));
      const end = nextMonday < monthEnd ? nextMonday : monthEnd;
      weeks.push(newBucket(`S${weeks.length + 1}`, `Semana del ${cursor.getDate()}`, cursor, end));
      cursor = end;
    }
    return weeks;
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

function addDays(date: Date, days: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days);
}

/** YYYY-MM-DD en local, que es como longDate espera una fecha sin hora. */
function isoDay(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

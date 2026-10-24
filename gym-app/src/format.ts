import type { LimitPeriod } from './api/gymClient';

/**
 * Fechas y textos de la app.
 *
 * <p>Las fechas del backend son dias de calendario ("2026-09-14"), sin hora ni zona:
 * se muestran cortando el texto y no pasando por {@code Date}, que las correria un
 * dia hacia atras en cualquier zona detras de UTC.
 */

/** "2026-09-14" -> "14/09". */
export function shortDay(iso: string): string {
  return `${iso.slice(8, 10)}/${iso.slice(5, 7)}`;
}

/** "2026-09-14" -> "14/09/2026". */
export function longDay(iso: string): string {
  return `${iso.slice(8, 10)}/${iso.slice(5, 7)}/${iso.slice(0, 4)}`;
}

/** Solo los digitos del DNI: el socio lo escribe con puntos, espacios o como quiera. */
export function digitsOnly(value: string): string {
  return value.replace(/\D/g, '');
}

/** "Ana Gómez" -> "Ana". */
export function firstName(fullName: string): string {
  return fullName.trim().split(/\s+/)[0] ?? '';
}

/** "2 de 3 días" en la semana; "3 de 9 ingresos" en el mes o el pase. En singular cuando corresponde. */
export function usage(used: number, limit: number, period: LimitPeriod): string {
  const unit = period === 'WEEK' ? ['día', 'días'] : ['ingreso', 'ingresos'];
  return `${used} de ${limit} ${limit === 1 ? unit[0] : unit[1]}`;
}

/** El título del bloque de ingresos. */
export function usageTitle(period: LimitPeriod): string {
  return period === 'WEEK' ? 'Esta semana' : period === 'MONTH' ? 'Este mes' : 'Este pase';
}

/** Hasta cuándo se cuentan, salvo en la semana (que todos saben que termina el domingo). */
export function usageUntil(period: LimitPeriod, windowEnd: string): string | null {
  if (period === 'WEEK') {
    return null;
  }
  return `${period === 'MONTH' ? 'Tu mes va' : 'Tu pase vale'} hasta el ${shortDay(windowEnd)}`;
}

/** 150000 -> "$ 150.000". Los montos del backend vienen como decimales numericos. */
export function money(value: number): string {
  return new Intl.NumberFormat('es-AR', {
    style: 'currency',
    currency: 'ARS',
    maximumFractionDigits: value % 1 === 0 ? 0 : 2,
  }).format(value);
}

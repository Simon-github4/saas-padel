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

/** "2 de 3 días" / "1 de 1 día". */
export function weekUsage(used: number, limit: number): string {
  return `${used} de ${limit} ${limit === 1 ? 'día' : 'días'}`;
}

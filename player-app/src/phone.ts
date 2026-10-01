/**
 * Control rápido del teléfono antes de mandar la reserva.
 *
 * <p>No valida el número: eso lo hace el servidor con libphonenumber, que sabe
 * las reglas de cada país y cada característica. Acá solo se ataja el error que
 * más se repite, que es escribir el celular como se lo dicta uno en su ciudad
 * (`15 415000`), sin código de área. Sin esto el jugador se enteraba recién
 * cuando el servidor lo rechazaba, y probaba formatos a ciegas.
 */

export const PHONE_MISSING = 'Necesitamos tu teléfono para confirmarte el turno.';

export const PHONE_NEEDS_AREA_CODE =
  'Falta el código de área. Escribilo así: 2262 15-###### o 11 ####-####.';

/** Qué le falta al teléfono, o nulo si no se ve nada raro. */
export function phoneProblem(raw: string): string | null {
  const text = raw.trim();
  if (!text) {
    return PHONE_MISSING;
  }
  // Con + o 00 es un número internacional: las reglas de cada país las sabe el servidor.
  if (text.startsWith('+') || /^\D*00/.test(text)) {
    return null;
  }
  // Sin el 0 de larga distancia, un número argentino tiene al menos 10 dígitos
  // (código de área más abonado, sin contar el 15). Y ningún código de área
  // empieza con 15: si arranca así, es el 15 de celular sin la característica.
  const digits = text.replace(/\D/g, '').replace(/^0/, '');
  if (digits.length < 10 || digits.startsWith('15')) {
    return PHONE_NEEDS_AREA_CODE;
  }
  return null;
}

/** Formatos argentinos, en un solo lugar para que las pantallas no los repitan. */

const LOCALE = 'es-AR';

export function money(amount: number): string {
  return new Intl.NumberFormat(LOCALE, {
    style: 'currency',
    currency: 'ARS',
    maximumFractionDigits: 0,
  }).format(amount);
}

/** Precio por persona, el numero que vende: el total del turno dividido los jugadores. */
export function perPerson(amount: number, playersPerCourt: number): string {
  return money(amount / playersPerCourt);
}

/** "martes 1 de septiembre" */
export function longDate(isoDate: string, timeZone?: string): string {
  const date = isoDate.length === 10 ? new Date(`${isoDate}T12:00:00`) : new Date(isoDate);
  return new Intl.DateTimeFormat(LOCALE, {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    timeZone,
  }).format(date);
}

/** "mar 1/9" para las pastillas del selector de dia. */
export function shortDate(isoDate: string): string {
  const date = new Date(`${isoDate}T12:00:00`);
  return new Intl.DateTimeFormat(LOCALE, { weekday: 'short', day: 'numeric', month: 'numeric' })
    .format(date)
    .replace('.', '');
}

/** "20:00" en la zona del club, no en la del telefono del jugador. */
export function clockTime(instant: string, timeZone: string): string {
  return new Intl.DateTimeFormat(LOCALE, {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
    timeZone,
  }).format(new Date(instant));
}

/** Fecha de hoy como YYYY-MM-DD, sin pasar por UTC. */
export function todayIso(): string {
  const now = new Date();
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 10);
}

export function addDays(isoDate: string, days: number): string {
  const date = new Date(`${isoDate}T12:00:00`);
  date.setDate(date.getDate() + days);
  return date.toISOString().slice(0, 10);
}

/** Minutos entre dos instantes ISO. */
export function durationMinutes(startInstant: string, endInstant: string): number {
  const start = new Date(startInstant);
  const end = new Date(endInstant);
  return Math.round((end.getTime() - start.getTime()) / 60000);
}

/** "4 h 30 min" / "2 h" / "45 min". */
export function formatHours(totalMinutes: number): string {
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  if (hours === 0) {
    return `${minutes} min`;
  }
  if (minutes === 0) {
    return `${hours} h`;
  }
  return `${hours} h ${minutes} min`;
}

/** "agosto", el mes solo, para los rotulos del grafico de actividad. */
export function monthName(date: Date): string {
  return new Intl.DateTimeFormat(LOCALE, { month: 'long' }).format(date);
}

/** Link para escribirle al club por WhatsApp. */
export function whatsappLink(phone: string, message?: string): string {
  const digits = phone.replace(/[^0-9]/g, '');
  return message
    ? `https://wa.me/${digits}?text=${encodeURIComponent(message)}`
    : `https://wa.me/${digits}`;
}

/**
 * Comparte el turno con quien elija el jugador. En el celular abre el selector
 * nativo (WhatsApp, mensajes, lo que tenga instalado); en desktop, donde
 * `navigator.share` no existe, cae a un link de WhatsApp sin numero fijo, que
 * abre el selector de contactos de WhatsApp Web.
 */
export async function shareBooking(text: string, url: string): Promise<void> {
  if (navigator.share) {
    try {
      await navigator.share({ text, url });
    } catch {
      // El usuario cerro el selector: no es un error que haya que mostrar.
    }
    return;
  }
  window.open(whatsappLink('', `${text} ${url}`), '_blank', 'noreferrer');
}

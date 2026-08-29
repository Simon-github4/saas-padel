/** Formatos argentinos, en un solo lugar para que las pantallas no los repitan. */

const LOCALE = 'es-AR';

export function money(amount: number): string {
  return new Intl.NumberFormat(LOCALE, {
    style: 'currency',
    currency: 'ARS',
    maximumFractionDigits: 0,
  }).format(amount);
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

/** Link para escribirle al club por WhatsApp. */
export function whatsappLink(phone: string, message?: string): string {
  const digits = phone.replace(/[^0-9]/g, '');
  return message
    ? `https://wa.me/${digits}?text=${encodeURIComponent(message)}`
    : `https://wa.me/${digits}`;
}

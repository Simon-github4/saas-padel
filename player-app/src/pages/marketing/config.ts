import { whatsappLink } from '../../format';

/** Editable de un solo lugar: nombre del producto y contacto de venta. */
/** El nombre en dos tramos: el logo pinta "Turnos" en blanco y "Padel" en ladrillo. */
export const BRAND_PARTS = ['Turnos', 'Padel'] as const;
export const BRAND = BRAND_PARTS.join('');
export const SALES_WHATSAPP = '5492262566185';
export const SALES_EMAIL = 'diazsimon1230@gmail.com';
export const SALES_MESSAGE =
  'Hola, tengo un club y quiero ver cómo funciona el sistema de reservas.';

export function salesWhatsappHref(): string {
  return whatsappLink(SALES_WHATSAPP, SALES_MESSAGE);
}

/** Precio único, por club. El alta y el cobro los hace el vendedor a mano. */
export const MONTHLY_PRICE_ARS = 35000;
export const TRIAL_DAYS = 7;

const SUBSCRIBE_MESSAGE = 'Hola, quiero contratar el plan para mi club.';
const SUBSCRIBE_SUBJECT = 'Quiero contratar el plan';

export function subscribeWhatsappHref(): string {
  return whatsappLink(SALES_WHATSAPP, SUBSCRIBE_MESSAGE);
}

const GYM_MESSAGE = 'Hola, mi club también tiene gimnasio y quiero saber del módulo.';

export function gymWhatsappHref(): string {
  return whatsappLink(SALES_WHATSAPP, GYM_MESSAGE);
}

export function subscribeMailHref(): string {
  return `mailto:${SALES_EMAIL}?subject=${encodeURIComponent(SUBSCRIBE_SUBJECT)}&body=${encodeURIComponent(SUBSCRIBE_MESSAGE)}`;
}

/**
 * Contacto para privacidad y soporte legal: separado del mail de ventas
 * porque uno es para clubes evaluando el producto y este es para cualquiera
 * (jugador o club) con una consulta sobre sus datos o los términos.
 */
export const LEGAL_EMAIL = 'diazsimon1230@gmail.com';

/**
 * Anota el recorrido del visitante y se lo manda al backend.
 *
 * <p>Mide la mitad de la historia que la base no tenía: hasta acá el sistema
 * sabía contar reservas, que es el final, y nada de lo que pasa antes. Con esto
 * se puede preguntar de cada cien que abren la ficha de un club cuántos
 * reservan, y si llegaron por la búsqueda global o por el link del club.
 *
 * <p>No hay servicio de terceros: el CSP de la app sólo admite scripts del
 * propio origen y la política de privacidad promete que no hay rastreo externo.
 * Esto es una llamada más a nuestra propia API, que `connect-src 'self'` ya
 * permite.
 *
 * <p>Nada de lo que hay acá puede tirar. Una medición que rompe la pantalla del
 * jugador cuesta muchísimo más que el dato que iba a dar, así que todo va
 * envuelto en try/catch y todo falla en silencio.
 */

import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';

const ENDPOINT = '/api/public/events';

/** La visita entera vive acá: identificador anónimo y de dónde llegó. */
const VISIT_KEY = 'pa_visita';
const SEQ_KEY = 'pa_visita_seq';

/** Los eventos se acumulan este rato antes de salir, para no mandar uno por uno. */
const FLUSH_DELAY_MS = 3000;

/** Tope del lote, igual al que valida el backend. */
const MAX_BATCH = 30;

export type EventName =
  | 'view'
  | 'search'
  | 'search_result_click'
  | 'club_step'
  | 'slot_click'
  | 'checkout_submit'
  | 'booking_created'
  | 'booking_failed'
  | 'link_expired'
  | 'waitlist_joined';

/** Propiedades opcionales. Cada evento usa las suyas; el resto viaja vacío. */
export interface EventProps {
  clubSlug?: string | null;
  results?: number;
  date?: string;
  from?: string;
  to?: string;
  clubs?: string;
  slotAt?: string;
  step?: number;
  paymentChoice?: string;
  bookingId?: string;
  detail?: string;
}

interface Visit {
  id: string;
  referrer?: string;
  utm?: string;
}

interface QueuedEvent extends EventProps {
  seq: number;
  name: EventName;
  path: string;
  fromSearch: boolean;
}

let queue: QueuedEvent[] = [];
let timer: ReturnType<typeof setTimeout> | null = null;

/**
 * Un paso del recorrido.
 *
 * <p>La ruta y el club los saca de la URL en vez de pedírselos a quien llama:
 * son los dos datos que más se repiten y los que peor se sienten si alguna
 * pantalla se los olvida.
 */
export function track(name: EventName, props: EventProps = {}): void {
  try {
    // Acá y no al mandar el lote: la visita congela de dónde llegó, y el
    // `utm_source` vive en la URL de entrada. Tres segundos después, que es
    // cuando sale el lote, el jugador ya puede estar en otra ruta.
    if (!currentVisit()) {
      return;
    }
    const path = window.location.pathname;
    queue.push({
      seq: nextSeq(),
      name,
      path: normalizePath(path),
      clubSlug: clubSlug(path),
      fromSearch: cameFromSearch(path, window.location.search),
      ...props,
    });
    if (queue.length >= MAX_BATCH) {
      flush();
      return;
    }
    if (timer === null) {
      timer = setTimeout(flush, FLUSH_DELAY_MS);
    }
  } catch {
    // Medir nunca puede romper la pantalla.
  }
}

/**
 * Igual que {@link track}, pero sale ya.
 *
 * <p>Para lo que ocurre justo antes de irse de la página: la reserva con seña
 * termina en un `window.location.href` hacia MercadoPago, y un evento que
 * todavía estaba esperando en la cola se pierde ahí mismo.
 */
export function trackNow(name: EventName, props: EventProps = {}): void {
  track(name, props);
  flush();
}

/** Emite un `view` por cada ruta que visita. */
export function usePageViews(): void {
  const location = useLocation();
  const lastPath = useRef<string | null>(null);

  useEffect(() => {
    // Sólo el pathname: en /buscar los filtros viven en la query y la cambian a
    // cada toque, y eso es una búsqueda, no una visita nueva. Y en desarrollo
    // StrictMode monta dos veces, que sin esta guarda son dos vistas.
    if (lastPath.current === location.pathname) {
      return;
    }
    lastPath.current = location.pathname;
    track('view');
  }, [location.pathname]);
}

/**
 * La ruta reducida a su forma, antes de salir del navegador.
 *
 * <p>`/manage/:token` y sus hermanos llevan en la URL el token que autoriza el
 * turno: es una credencial, y no tiene por qué existir en una tabla de
 * estadísticas. El backend vuelve a normalizar lo que le llegue, pero el token
 * ni siquiera sale de acá.
 */
export function normalizePath(pathname: string): string {
  const [, first, second] = pathname.split('/');
  if (!first) {
    return '/';
  }
  if (['buscar', 'login', 'account', 'forgot-password', 'privacidad', 'terminos'].includes(first)) {
    return `/${first}`;
  }
  if (['manage', 'confirm', 'turno', 'reset-password'].includes(first)) {
    return `/${first}/:token`;
  }
  if (first === 'club' && second) {
    return '/club/:slug';
  }
  return '/otro';
}

function clubSlug(pathname: string): string | undefined {
  const [, first, second] = pathname.split('/');
  return first === 'club' && second ? second : undefined;
}

/** El link que arma la búsqueda global siempre trae el horario ya elegido. */
function cameFromSearch(pathname: string, search: string): boolean {
  return pathname.startsWith('/club/') && new URLSearchParams(search).has('hora');
}

function flush(): void {
  if (timer !== null) {
    clearTimeout(timer);
    timer = null;
  }
  if (queue.length === 0) {
    return;
  }
  const visit = currentVisit();
  if (!visit) {
    queue = [];
    return;
  }

  const body = JSON.stringify({
    sessionId: visit.id,
    referrer: visit.referrer,
    utmSource: visit.utm,
    events: queue,
  });
  // La cola se vacía antes de mandar: si el envío falla, se pierde el lote y no
  // la pantalla. Reintentar sería acumular eventos viejos de una visita que ya
  // terminó.
  queue = [];

  try {
    const blob = new Blob([body], { type: 'application/json' });
    // sendBeacon y no fetch: sobrevive a que la pestaña se cierre o navegue a
    // otro sitio, que es exactamente cuando se manda el último lote.
    if (!navigator.sendBeacon?.(ENDPOINT, blob)) {
      void fetch(ENDPOINT, { method: 'POST', body: blob, keepalive: true }).catch(() => {});
    }
  } catch {
    // Sin medición, pero con app.
  }
}

/**
 * La visita: un identificador anónimo y el origen con el que llegó.
 *
 * <p>Vive en sessionStorage y no en localStorage a propósito: muere al cerrar la
 * pestaña, así que une los pasos de un mismo recorrido pero no reconoce a nadie
 * entre visitas. Eso es lo que hace que siga siendo cierto lo que dice la
 * política de privacidad sobre no rastrear.
 *
 * <p>El origen se congela cuando arranca la visita. Después de la primera
 * navegación interna el `utm_source` ya no está en la URL y `document.referrer`
 * pierde valor, y justamente el origen es de la visita entera.
 *
 * <p>Límite conocido: abrir un link en una pestaña nueva copia el
 * sessionStorage, así que las dos pestañas comparten identificador y contador.
 * La que llegue segunda choca contra la unicidad de (visita, orden) y sus
 * eventos se descartan. Es una pérdida de medición, no un dato torcido, y pasa
 * sólo con el ctrl+clic; resolverlo pide un identificador por pestaña que no
 * vale lo que cuesta.
 */
function currentVisit(): Visit | null {
  try {
    const stored = sessionStorage.getItem(VISIT_KEY);
    if (stored) {
      return JSON.parse(stored) as Visit;
    }
    const visit: Visit = {
      id: newId(),
      referrer: externalReferrer(),
      utm: new URLSearchParams(window.location.search).get('utm_source') ?? undefined,
    };
    sessionStorage.setItem(VISIT_KEY, JSON.stringify(visit));
    return visit;
  } catch {
    // Navegador con el almacenamiento bloqueado: sin visita no hay hilo que
    // seguir, y medir pasos sueltos no dice nada.
    return null;
  }
}

/** Sólo el host, y sólo si es de afuera: navegar adentro del sitio no es un origen. */
function externalReferrer(): string | undefined {
  try {
    const host = new URL(document.referrer).host;
    return host && host !== window.location.host ? host : undefined;
  } catch {
    return undefined;
  }
}

/**
 * La posición del evento dentro de la visita.
 *
 * <p>El orden no puede salir del reloj del servidor: los eventos viajan en
 * lotes y varios entran con el mismo instante, justo cuando el orden importa.
 * Va en sessionStorage para que recargar la página siga la cuenta en vez de
 * empezarla de nuevo y chocar contra los eventos ya guardados.
 */
function nextSeq(): number {
  try {
    const seq = Number(sessionStorage.getItem(SEQ_KEY) ?? '0') + 1;
    sessionStorage.setItem(SEQ_KEY, String(seq));
    return seq;
  } catch {
    return 1;
  }
}

function newId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID();
  }
  // Contextos sin crypto.randomUUID (http que no sea localhost). No necesita ser
  // criptográfico: sólo distinguir una visita de otra.
  return '10000000-1000-4000-8000-100000000000'.replace(/[018]/g, (char) =>
    ((Number(char) ^ Math.floor(Math.random() * 16)) & 15).toString(16),
  );
}

if (typeof document !== 'undefined') {
  // Irse de la página es el momento en que hay que mandar lo que quedó en la
  // cola. visibilitychange cubre el celular que cambia de app, que es donde
  // 'pagehide' a veces no llega.
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') {
      flush();
    }
  });
  window.addEventListener('pagehide', flush);
}

/**
 * Cliente de la API del jugador.
 *
 * <p>El backend devuelve los errores ya redactados para mostrarse en pantalla, asi
 * que aca no se reescriben: se propagan tal cual, con su codigo. El unico que la
 * app trata distinto es SLOT_TAKEN, porque ahi no hay nada que corregir sino una
 * grilla que refrescar.
 */

export type PaymentChoice = 'DEPOSIT_ONLINE' | 'PAY_AT_CLUB';

export interface CourtAvailability {
  courtId: string;
  courtName: string;
  price: number;
}

export interface Slot {
  startTime: string;
  endTime: string;
  startsAt: string;
  endsAt: string;
  available: CourtAvailability[];
  promo: boolean;
}

export interface Amenity {
  icon: string;
  title: string;
  description: string | null;
}

export interface Club {
  slug: string;
  name: string;
  timeZone: string;
  whatsappNumber: string;
  allowUnpaidBooking: boolean;
  acceptsOnlinePayments: boolean;
  depositPercentage: number;
  cancellationLimitHours: number;
  bookingHorizonDays: number;
  tagline: string | null;
  address: string | null;
  city: string | null;
  mapsUrl: string | null;
  /** Coordenadas del club. Sin ellas no se puede previsualizar el mapa. */
  latitude: number | null;
  longitude: number | null;
  heroImageUrl: string | null;
  heroHeadline: string | null;
  heroCtaLabel: string | null;
  /** 0-100: cuanto se oscurece la foto de portada. Lo gradua el club. */
  heroOverlay: number;
  /** Diseño de portada que eligió el club. */
  heroVariant: 'CLASSIC' | 'SCOREBOARD' | 'COURT_SPLIT';
  playersPerCourt: number;
  /** Paleta clara u oscura de la app. */
  themeMode: 'DARK' | 'LIGHT';
  /** Acentos de marca en hex. Null = el club no los configuró, va el de fábrica. */
  primaryColor: string | null;
  secondaryColor: string | null;
  amenities: Amenity[];
}

export interface Availability {
  club: Club;
  date: string;
  slotDurationMinutes: number;
  courts: { id: string; name: string }[];
  slots: Slot[];
}

/** Un club en el filtro de la búsqueda global. */
export interface ClubOption {
  slug: string;
  name: string;
  city: string | null;
  bookingHorizonDays: number;
}

/** Un horario libre en un club concreto, ya listo para pintar como resultado. */
export interface SearchMatch {
  clubSlug: string;
  clubName: string;
  city: string | null;
  startTime: string;
  endTime: string;
  startsAt: string;
  cheapestPrice: number;
  playersPerCourt: number;
  freeCourts: number;
  promo: boolean;
}

export interface SearchResult {
  date: string;
  clubs: ClubOption[];
  /** Ya viene ordenada por horario: el orden lo decide el backend. */
  matches: SearchMatch[];
}

export interface BookingCreated {
  bookingId: string;
  status: string;
  managementUrl: string;
  /** Token del link de gestión, para navegar en la app sin depender de la base-url. */
  managementToken: string;
  /** Link de solo lectura para mandarle a los demás jugadores del turno. */
  shareUrl: string;
  checkoutUrl: string | null;
  awaitingWhatsappConfirmation: boolean;
  totalPrice: number;
  depositAmount: number;
  message: string;
}

export interface BookingDetail {
  bookingId: string;
  status: string;
  clubName: string;
  clubSlug: string;
  clubWhatsapp: string;
  courtName: string;
  startTime: string;
  endTime: string;
  totalPrice: number;
  paidAmount: number;
  balanceDue: number;
  cancellableOnline: boolean;
  cancellationLimitHours: number;
  cancellationHint: string | null;
  shareUrl: string;
}

/** Lo que ve un tercero que recibe el link para compartir el turno: sin datos de pago. */
export interface BookingShareInfo {
  status: string;
  clubName: string;
  clubSlug: string;
  courtName: string;
  startTime: string;
  endTime: string;
  bookedByName: string | null;
}

export interface Cancellation {
  status: string;
  refundNeeded: boolean;
  clubWhatsapp: string;
  message: string;
}

/** Confirmación de que quedó anotado en la lista de espera de un horario lleno. */
export interface WaitlistJoined {
  message: string;
}

export interface PlayerSession {
  token: string;
  expiresAt: string;
  accountId: string;
  email: string;
  emailVerified: boolean;
  /** Dato de contacto, no la identidad de la cuenta. Null hasta que reserva logueado la primera vez. */
  phoneNumber: string | null;
  /** Nombre con el que el jugador reservó algún turno logueado. Null si todavía no. */
  displayName: string | null;
}

export interface PlayerConfig {
  googleClientId: string | null;
}

/** Un turno del historial global, con el link para verlo y gestionarlo. */
export interface BookingHistoryItem {
  bookingId: string;
  clubName: string;
  clubSlug: string;
  courtName: string;
  startTime: string;
  endTime: string;
  status: string;
  totalPrice: number;
  paidAmount: number;
  managementToken: string;
}

/** Error de la API con el mensaje que el backend escribio para el jugador. */
export class ApiError extends Error {
  constructor(
    message: string,
    readonly code: string,
    readonly status: number,
  ) {
    super(message);
  }

  /** Alguien tomo el turno mientras el jugador miraba la grilla. */
  get slotTaken() {
    return this.code === 'SLOT_TAKEN';
  }
}

async function request<T>(path: string, init?: RequestInit & { token?: string }): Promise<T> {
  const { token, ...rest } = init ?? {};
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  let response: Response;
  try {
    response = await fetch(`/api/public${path}`, { headers, ...rest });
  } catch {
    throw new ApiError('No pudimos conectarnos. Revisá tu conexión.', 'NETWORK', 0);
  }

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new ApiError(
      body?.message ?? 'Tuvimos un problema. Probá de nuevo en un momento.',
      body?.code ?? 'UNKNOWN',
      response.status,
    );
  }
  // 202/204 (arranca el login, cierra la sesion) no traen cuerpo: leerlo como
  // JSON directo tira, porque un string vacio no es JSON valido.
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const api = {
  /**
   * Turnos libres en varios clubes a la vez. Sin slug, porque es la pantalla de
   * quien todavía no eligió dónde jugar.
   */
  search: (params: { date: string; from: string; to: string; clubs: string[] }) => {
    const query = new URLSearchParams({
      date: params.date,
      from: params.from,
      to: params.to,
    });
    if (params.clubs.length > 0) {
      query.set('clubs', params.clubs.join(','));
    }
    return request<SearchResult>(`/search?${query.toString()}`);
  },

  availability: (slug: string, date: string) =>
    request<Availability>(`/${slug}/availability?date=${date}`),

  book: (
    slug: string,
    body: {
      courtId: string;
      startTime: string;
      fullName: string;
      phoneNumber: string;
      paymentChoice: PaymentChoice;
    },
  ) => request<BookingCreated>(`/${slug}/bookings`, { method: 'POST', body: JSON.stringify(body) }),

  joinWaitlist: (
    slug: string,
    body: { startTime: string; fullName: string; phoneNumber: string },
  ) => request<WaitlistJoined>(`/${slug}/waitlist`, { method: 'POST', body: JSON.stringify(body) }),

  confirm: (token: string) => request<BookingDetail>(`/confirm/${token}`, { method: 'POST' }),

  booking: (token: string) => request<BookingDetail>(`/manage/${token}`),

  cancel: (token: string) => request<Cancellation>(`/manage/${token}/cancel`, { method: 'POST' }),

  share: (token: string) => request<BookingShareInfo>(`/share/${token}`),
};

/**
 * Login del jugador y su cuenta global, separado de {@link api}: ese es todo por
 * club/token-en-URL, y mezclar los dos borraria esa distincion.
 */
export const playerApi = {
  /** Client ID de Google, para mostrar (o no) el botón de "Seguir con Google". */
  config: () => request<PlayerConfig>('/player/config'),

  /** Todavía no crea la cuenta: manda el código/link de confirmación. Ver {@link confirmSignup}. */
  register: (email: string, password: string, displayName?: string, phoneNumber?: string) =>
    request<void>('/player/register', {
      method: 'POST',
      body: JSON.stringify({ email, password, displayName, phoneNumber }),
    }),

  /** Confirma el alta con el código de 6 dígitos: recién acá se crea la cuenta y se abre sesión. */
  confirmSignup: (email: string, code: string) =>
    request<PlayerSession>('/player/register/confirm', {
      method: 'POST',
      body: JSON.stringify({ email, code }),
    }),

  login: (email: string, password: string) =>
    request<PlayerSession>('/player/login', { method: 'POST', body: JSON.stringify({ email, password }) }),

  loginWithGoogle: (idToken: string) =>
    request<PlayerSession>('/player/login/google', { method: 'POST', body: JSON.stringify({ idToken }) }),

  requestPasswordReset: (email: string) =>
    request<void>('/player/password/forgot', { method: 'POST', body: JSON.stringify({ email }) }),

  resetPassword: (token: string, newPassword: string) =>
    request<void>('/player/password/reset', { method: 'POST', body: JSON.stringify({ token, newPassword }) }),

  /** Guarda el nombre (y, si todavía no tenía, el teléfono) con el que se presentó al reservar. */
  updateProfile: (token: string, name: string, phoneNumber?: string) =>
    request<void>('/player/profile', { method: 'PUT', token, body: JSON.stringify({ name, phoneNumber }) }),

  logout: (token: string) => request<void>('/player/logout', { method: 'POST', token }),

  bookingHistory: (token: string) => request<BookingHistoryItem[]>('/player/bookings', { token }),
};

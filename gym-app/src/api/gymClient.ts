/**
 * Cliente de la API del gimnasio.
 *
 * <p>Los errores del backend vienen redactados para mostrarse en pantalla: aca no
 * se reescriben, se propagan con su codigo. La sesion viaja como Bearer (no hay
 * cookies), asi que tampoco hace falta CSRF.
 */

import type { Position } from '../geolocation';

/** Lo que se sabe del gimnasio antes de entrar. */
export interface PublicConfig {
  clubName: string;
  /** El club pide clave ademas del DNI. Lo normal es que no. */
  passwordRequired: boolean;
  /** Alguna sede verifica la ubicacion: la app se la va a pedir al registrar el ingreso. */
  locationRequired: boolean;
  /** La portada del club, usada tambien para el icono instalable de la PWA. */
  heroImageUrl: string | null;
  themeMode: 'DARK' | 'LIGHT';
  primaryColor: string | null;
  secondaryColor: string | null;
}

export interface SessionResponse {
  token: string;
  expiresAt: string;
  fullName: string;
  mustChangePassword: boolean;
}

export interface Membership {
  startsOn: string;
  endsOn: string;
  daysPerWeek: number;
  sedes: string[];
}

export interface RecentCheckin {
  date: string;
  sedeName: string;
}

export interface Me {
  fullName: string;
  /** La cuota vigente, o la ultima que tuvo si ya vencio. Null si nunca tuvo. */
  membership: Membership | null;
  /** Si la cuota vale hoy. */
  valid: boolean;
  weekUsed: number;
  weekLimit: number;
  checkedInToday: boolean;
  recent: RecentCheckin[];
}

export interface CheckInResult {
  sedeName: string;
  /** Ya tenia el ingreso de hoy: escanear dos veces no es un error. */
  alreadyRegistered: boolean;
  weekUsed: number;
  weekLimit: number;
  validUntil: string;
}

export class ApiError extends Error {
  constructor(
    message: string,
    readonly code: string,
    readonly status: number,
  ) {
    super(message);
  }

  /** La sesion no existe, vencio o se cerro (por ejemplo, el mostrador reseteo la clave). */
  get sessionExpired() {
    return this.code === 'SESSION_EXPIRED';
  }

  /** Sigue con la clave temporal del mostrador: hasta que la cambie no puede usar el resto. */
  get passwordChangeRequired() {
    return this.code === 'PASSWORD_CHANGE_REQUIRED';
  }

  /** La sede verifica la ubicacion y el celular no la mando. */
  get locationRequired() {
    return this.code === 'LOCATION_REQUIRED';
  }

  /** El club no tiene el modulo de gimnasio. */
  get unavailable() {
    return this.status === 404;
  }
}

async function request<T>(
  url: string,
  init?: { method?: string; body?: unknown; token?: string },
): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (init?.token) {
    headers.Authorization = `Bearer ${init.token}`;
  }

  let response: Response;
  try {
    response = await fetch(url, {
      method: init?.method ?? 'GET',
      headers,
      body: init?.body === undefined ? undefined : JSON.stringify(init.body),
    });
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
  // El logout responde 204, sin cuerpo: leerlo como JSON directo tiraria.
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

const base = (slug: string) => `/api/public/${encodeURIComponent(slug)}/gym`;

export const gymApi = {
  /** Es 404 si el club no tiene el gimnasio. */
  config: (slug: string) => request<PublicConfig>(`${base(slug)}/config`),

  /** La clave solo viaja si el club la pide: con solo el DNI, no. */
  login: (slug: string, dni: string, password?: string) =>
    request<SessionResponse>(`${base(slug)}/login`, {
      method: 'POST',
      body: password ? { dni, password } : { dni },
    }),

  logout: (slug: string, token: string) =>
    request<void>(`${base(slug)}/logout`, { method: 'POST', token }),

  changePassword: (slug: string, token: string, currentPassword: string, newPassword: string) =>
    request<SessionResponse>(`${base(slug)}/password/change`, {
      method: 'POST',
      token,
      body: { currentPassword, newPassword },
    }),

  me: (slug: string, token: string) => request<Me>(`${base(slug)}/me`, { token }),

  /**
   * El socio sale de la sesion: el pedido no lleva ningun id de socio. La ubicacion es la del
   * celular; el servidor la exige solo si la sede la verifica.
   */
  checkIn: (slug: string, token: string, qrToken: string, position?: Position) =>
    request<CheckInResult>(`${base(slug)}/checkin`, {
      method: 'POST',
      token,
      body: position ? { qrToken, latitude: position.latitude, longitude: position.longitude } : { qrToken },
    }),
};

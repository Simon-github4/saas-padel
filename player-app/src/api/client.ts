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
}

export interface Availability {
  club: Club;
  date: string;
  slotDurationMinutes: number;
  courts: { id: string; name: string }[];
  slots: Slot[];
}

export interface BookingCreated {
  bookingId: string;
  status: string;
  managementUrl: string;
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
}

export interface Cancellation {
  status: string;
  refundNeeded: boolean;
  clubWhatsapp: string;
  message: string;
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

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`/api/public${path}`, {
      headers: { 'Content-Type': 'application/json' },
      ...init,
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
  return response.json() as Promise<T>;
}

export const api = {
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

  confirm: (token: string) => request<BookingDetail>(`/confirm/${token}`, { method: 'POST' }),

  booking: (token: string) => request<BookingDetail>(`/manage/${token}`),

  cancel: (token: string) => request<Cancellation>(`/manage/${token}/cancel`, { method: 'POST' }),
};

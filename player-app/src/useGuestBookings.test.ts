import { describe, expect, it } from 'vitest';
import { ApiError, type BookingDetail } from './api/client';
import { guestUpdate } from './useGuestBookings';

function detalle(status: string): BookingDetail {
  return {
    bookingId: 'b1',
    status,
    clubName: 'Pádel Necochea',
    clubSlug: 'club-necochea',
    clubWhatsapp: '542262400000',
    courtName: 'Cancha 2',
    startTime: '2026-10-12T22:00:00Z',
    endTime: '2026-10-12T23:30:00Z',
    totalPrice: 18000,
    paidAmount: 0,
    balanceDue: 18000,
    cancellableOnline: true,
    cancellationLimitHours: 2,
    cancellationHint: null,
    shareUrl: 'http://localhost/turno/x',
    themeMode: 'DARK',
    primaryColor: null,
    secondaryColor: null,
  };
}

describe('guestUpdate', () => {
  it('trae el estado y los datos del turno como están hoy', () => {
    expect(guestUpdate({ status: 'fulfilled', value: detalle('CANCELLED') })).toEqual({
      status: 'CANCELLED',
      clubName: 'Pádel Necochea',
      courtName: 'Cancha 2',
      startTime: '2026-10-12T22:00:00Z',
      endTime: '2026-10-12T23:30:00Z',
    });
  });

  it('un link que ya no existe se borra', () => {
    const noExiste = new ApiError('Este link no corresponde a ningun turno', 'NOT_FOUND', 404);
    expect(guestUpdate({ status: 'rejected', reason: noExiste })).toBeNull();
  });

  it('sin conexión o con el servidor caído no se sabe nada, y no se toca', () => {
    const sinRed = new ApiError('No pudimos conectarnos. Revisá tu conexión.', 'NETWORK', 0);
    const caido = new ApiError('Tuvimos un problema.', 'UNKNOWN', 500);
    expect(guestUpdate({ status: 'rejected', reason: sinRed })).toBeUndefined();
    expect(guestUpdate({ status: 'rejected', reason: caido })).toBeUndefined();
    expect(guestUpdate({ status: 'rejected', reason: new Error('otra cosa') })).toBeUndefined();
  });

  it('un 404 que no viene de nuestra API no borra nada', () => {
    // La página de error de un proxy en medio de un deploy: sin cuerpo JSON, el
    // cliente lo arma con código UNKNOWN.
    const proxy = new ApiError('Tuvimos un problema.', 'UNKNOWN', 404);
    expect(guestUpdate({ status: 'rejected', reason: proxy })).toBeUndefined();
  });
});

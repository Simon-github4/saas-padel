import { describe, expect, it } from 'vitest';
import type { BookingHistoryItem, RecurringItem } from './api/client';
import { longDate } from './format';
import { dayLabel, fixedThisWeek, isFixed, manageLabel, nextBooking, splitBookings, whenLabel } from './upcoming';

/** Un jueves a la tarde. */
const AHORA = new Date(2026, 8, 10, 15, 0); // 10 de septiembre de 2026, 15:00 local

function turno(empieza: Date, status = 'CONFIRMED', minutos = 90): BookingHistoryItem {
  const termina = new Date(empieza.getTime() + minutos * 60 * 1000);
  return {
    bookingId: `t-${empieza.toISOString()}-${status}`,
    clubName: 'Pádel Necochea',
    clubSlug: 'club-necochea',
    courtName: 'Cancha 1',
    startTime: empieza.toISOString(),
    endTime: termina.toISOString(),
    status,
    totalPrice: 28000,
    paidAmount: 0,
    managementToken: 'tok',
  };
}

describe('splitBookings', () => {
  it('separa hoy, lo que viene y lo que pasó, con los dos primeros del más cercano al más lejano', () => {
    const hoyALaNoche = turno(new Date(2026, 8, 10, 21, 0));
    const hoyALaTarde = turno(new Date(2026, 8, 10, 17, 0));
    const mananaTemprano = turno(new Date(2026, 8, 11, 0, 30));
    const finDeMes = turno(new Date(2026, 8, 30, 20, 0));
    const ayer = turno(new Date(2026, 8, 9, 20, 0), 'COMPLETED');

    // El historial llega del más nuevo al más viejo.
    const { today, upcoming, past } = splitBookings(
      [finDeMes, mananaTemprano, hoyALaNoche, hoyALaTarde, ayer],
      AHORA,
    );

    expect(today).toEqual([hoyALaTarde, hoyALaNoche]);
    expect(upcoming).toEqual([mananaTemprano, finDeMes]);
    expect(past).toEqual([ayer]);
  });

  it('mantiene en hoy el turno que se está jugando', () => {
    const enJuego = turno(new Date(2026, 8, 10, 14, 30));
    expect(splitBookings([enJuego], AHORA).today).toEqual([enJuego]);
  });

  it('incluye en hoy los que esperan pago o confirmación', () => {
    const sinConfirmar = turno(new Date(2026, 8, 10, 20, 0), 'AWAITING_CONFIRMATION');
    const esperandoPago = turno(new Date(2026, 8, 10, 22, 0), 'DRAFT');
    expect(splitBookings([esperandoPago, sinConfirmar], AHORA).today).toEqual([
      sinConfirmar,
      esperandoPago,
    ]);
  });

  it('un turno que el club cerró al cobrarlo antes del partido va según su fecha', () => {
    const cobradoHoy = turno(new Date(2026, 8, 10, 18, 0), 'COMPLETED');
    const cobradoManana = turno(new Date(2026, 8, 11, 20, 0), 'COMPLETED');
    const { today, upcoming, past } = splitBookings([cobradoManana, cobradoHoy], AHORA);
    expect(today).toEqual([cobradoHoy]);
    expect(upcoming).toEqual([cobradoManana]);
    expect(past).toEqual([]);
  });

  it('un cancelado de una hora que no llegó va a próximos, no a hoy ni a anteriores', () => {
    const cancelado = turno(new Date(2026, 8, 10, 20, 0), 'CANCELLED');
    const { today, upcoming, past } = splitBookings([cancelado], AHORA);
    expect(today).toEqual([]);
    expect(upcoming).toEqual([cancelado]);
    expect(past).toEqual([]);
  });

  it('todo lo que ya terminó va a anteriores, sea cual sea su estado, en el orden en que llegó', () => {
    const terminadoHoy = turno(new Date(2026, 8, 10, 9, 0));
    const canceladoAyer = turno(new Date(2026, 8, 9, 20, 0), 'CANCELLED');
    const sinConfirmarVencido = turno(new Date(2026, 8, 8, 20, 0), 'AWAITING_CONFIRMATION');
    const { past } = splitBookings([terminadoHoy, canceladoAyer, sinConfirmarVencido], AHORA);
    expect(past).toEqual([terminadoHoy, canceladoAyer, sinConfirmarVencido]);
  });
});

describe('whenLabel', () => {
  it('dice cuánto falta, o "Ahora" si ya empezó', () => {
    expect(whenLabel(turno(new Date(2026, 8, 10, 14, 30)), AHORA)).toBe('Ahora');
    expect(whenLabel(turno(new Date(2026, 8, 10, 15, 40)), AHORA)).toBe('En 40 min');
    expect(whenLabel(turno(new Date(2026, 8, 10, 17, 30)), AHORA)).toBe('En 2 h 30 min');
    expect(whenLabel(turno(new Date(2026, 8, 10, 21, 0)), AHORA)).toBe('En 6 h');
  });
});

function fijo(proxima: Date | null, status = 'CONFIRMED'): RecurringItem {
  return {
    recurringId: `f-${proxima?.toISOString() ?? 'sin-fecha'}`,
    clubName: 'Pádel Necochea',
    clubSlug: 'club-necochea',
    timeZone: 'America/Argentina/Buenos_Aires',
    courtName: 'Cancha 2',
    dayOfWeek: proxima ? ((proxima.getDay() + 6) % 7) + 1 : 2,
    startTime: '20:00',
    durationMinutes: 90,
    validUntil: null,
    next: proxima
      ? {
          startTime: proxima.toISOString(),
          endTime: new Date(proxima.getTime() + 90 * 60 * 1000).toISOString(),
          status,
        }
      : null,
  };
}

describe('fixedThisWeek', () => {
  it('trae la próxima fecha de cada fijo si cae en los próximos siete días', () => {
    const hoy = fijo(new Date(2026, 8, 10, 20, 0));
    const miercoles = fijo(new Date(2026, 8, 16, 21, 0));
    const jueves = fijo(new Date(2026, 8, 17, 9, 0)); // octavo día: afuera

    const fechas = fixedThisWeek([hoy, miercoles, jueves], AHORA);

    expect(fechas.map((fecha) => fecha.recurringId)).toEqual([hoy.recurringId, miercoles.recurringId]);
    expect(fechas[0]).toMatchObject({ clubName: 'Pádel Necochea', courtName: 'Cancha 2', status: 'CONFIRMED' });
  });

  it('no trae nada de un fijo sin fecha generada', () => {
    expect(fixedThisWeek([fijo(null)], AHORA)).toEqual([]);
  });

  it('la fecha de hoy va a Hoy y la del sábado a Próximos, mezcladas con los demás turnos', () => {
    const fechas = fixedThisWeek([fijo(new Date(2026, 8, 10, 20, 0)), fijo(new Date(2026, 8, 12, 19, 0))], AHORA);
    const viernes = turno(new Date(2026, 8, 11, 21, 0));

    const { today, upcoming } = splitBookings([viernes, ...fechas], AHORA);

    expect(today).toHaveLength(1);
    expect(isFixed(today[0])).toBe(true);
    expect(upcoming.map((item) => isFixed(item))).toEqual([false, true]);
  });
});

describe('nextBooking', () => {
  it('trae el más cercano que todavía no terminó y cuenta los que vienen después', () => {
    const sabado = turno(new Date(2026, 8, 12, 19, 0));
    const hoyALaNoche = turno(new Date(2026, 8, 10, 21, 0));
    const finDeMes = turno(new Date(2026, 8, 30, 20, 0));
    const ayer = turno(new Date(2026, 8, 9, 20, 0), 'COMPLETED');

    expect(nextBooking([finDeMes, sabado, hoyALaNoche, ayer], AHORA)).toEqual({ next: hoyALaNoche, more: 2 });
  });

  it('salta los cancelados: no es un turno que se vaya a jugar', () => {
    const cancelado = turno(new Date(2026, 8, 10, 18, 0), 'CANCELLED');
    const manana = turno(new Date(2026, 8, 11, 20, 0));
    expect(nextBooking([cancelado, manana], AHORA)).toEqual({ next: manana, more: 0 });
  });

  it('el que se está jugando sigue siendo el próximo', () => {
    const enJuego = turno(new Date(2026, 8, 10, 14, 30));
    expect(nextBooking([enJuego], AHORA).next).toBe(enJuego);
  });

  it('sin nada por delante no trae ninguno', () => {
    expect(nextBooking([turno(new Date(2026, 8, 9, 20, 0))], AHORA)).toEqual({ next: null, more: 0 });
    expect(nextBooking([], AHORA)).toEqual({ next: null, more: 0 });
  });
});

describe('dayLabel', () => {
  it('hoy dice cuánto falta, o "Ahora" si ya empezó', () => {
    expect(dayLabel(turno(new Date(2026, 8, 10, 14, 30)), AHORA)).toBe('Ahora');
    expect(dayLabel(turno(new Date(2026, 8, 10, 15, 40)), AHORA)).toBe('Hoy, en 40 min');
  });

  it('mañana es "Mañana"', () => {
    expect(dayLabel(turno(new Date(2026, 8, 11, 20, 0)), AHORA)).toBe('Mañana');
    expect(dayLabel(turno(new Date(2026, 8, 11, 23, 0)), AHORA)).toBe('Mañana');
  });

  it('el de la madrugada dice además cuánto falta, para que no se lea como mañana a la noche', () => {
    const nochePasadaLasDoce = turno(new Date(2026, 8, 11, 0, 30));
    expect(dayLabel(nochePasadaLasDoce, AHORA)).toBe('Mañana, en 9 h 30 min');
    expect(dayLabel(nochePasadaLasDoce, new Date(2026, 8, 10, 22, 0))).toBe('Mañana, en 2 h 30 min');
  });

  it('más adelante, la fecha', () => {
    // Contra longDate y no contra el texto: la coma después del día cambia según
    // la versión de ICU del navegador.
    const sabado = turno(new Date(2026, 8, 12, 19, 0));
    expect(dayLabel(sabado, AHORA)).toBe(longDate(sabado.startTime));
    expect(dayLabel(sabado, AHORA)).toMatch(/^sábado,? 12 de septiembre$/);
  });
});

describe('manageLabel', () => {
  it('ofrece cancelar el que todavía no empezó, también si espera confirmación', () => {
    expect(manageLabel(turno(new Date(2026, 8, 10, 20, 0)), AHORA)).toBe('Ver o cancelar');
    expect(manageLabel(turno(new Date(2026, 8, 12, 20, 0), 'AWAITING_CONFIRMATION'), AHORA)).toBe('Ver o cancelar');
  });

  it('no promete cancelar lo que la página del turno no deja cancelar', () => {
    // Esperando pago, o cobrado entero en el mostrador antes del partido: la
    // página del turno no ofrece cancelarlos, ni online ni por WhatsApp.
    expect(manageLabel(turno(new Date(2026, 8, 12, 20, 0), 'DRAFT'), AHORA)).toBe('Ver turno');
    expect(manageLabel(turno(new Date(2026, 8, 12, 20, 0), 'COMPLETED'), AHORA)).toBe('Ver turno');
  });

  it('el cancelado, el que ya pasó y el que se está jugando solo se miran', () => {
    expect(manageLabel(turno(new Date(2026, 8, 12, 20, 0), 'CANCELLED'), AHORA)).toBe('Ver turno');
    expect(manageLabel(turno(new Date(2026, 8, 9, 20, 0)), AHORA)).toBe('Ver turno');
    expect(manageLabel(turno(new Date(2026, 8, 10, 14, 30)), AHORA)).toBe('Ver turno');
    expect(manageLabel(turno(new Date(2026, 8, 9, 20, 0), 'NO_SHOW'), AHORA)).toBe('Ver turno');
  });
});

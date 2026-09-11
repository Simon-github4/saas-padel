import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  forgetGuestBooking,
  readGuestBookings,
  rememberGuestBooking,
  type GuestBooking,
} from './guestBookings';

/**
 * La lista de turnos reservados sin cuenta.
 *
 * <p>Lo que más importa acá es la poda, porque lo que se guarda en esa lista es
 * el token con el que se cancela un turno: una credencial que sobrevive a su
 * utilidad es sólo superficie expuesta.
 *
 * <p>El reloj se congela en un instante fijo y los turnos se escriben en
 * relación a él. Con fechas calculadas sobre el reloj real, una corrida podría
 * caer justo en el borde de la hora de gracia y fallar sola una vez cada tanto.
 */

const AHORA = new Date('2026-09-12T20:00:00Z');
const STORAGE_KEY = 'padel_guest_bookings';

/**
 * localStorage en memoria. Preferido sobre traer jsdom entero: nada de lo que
 * se prueba acá toca el DOM, y una dependencia que existe para cuatro métodos
 * es peso muerto.
 */
function stubLocalStorage() {
  const data = new Map<string, string>();
  return {
    getItem: (key: string) => data.get(key) ?? null,
    setItem: (key: string, value: string) => void data.set(key, value),
    removeItem: (key: string) => void data.delete(key),
    clear: () => data.clear(),
    key: (index: number) => [...data.keys()][index] ?? null,
    get length() {
      return data.size;
    },
  } as Storage;
}

/** Un turno de ejemplo, ubicado en el tiempo por cuándo empieza y cuándo termina. */
function turno(id: string, empieza: Date, termina: Date | null): GuestBooking {
  return {
    bookingId: id,
    managementToken: `token-de-${id}`,
    clubName: 'Pádel Necochea',
    clubSlug: 'club-necochea',
    courtName: 'Cancha 1',
    startTime: empieza.toISOString(),
    ...(termina ? { endTime: termina.toISOString() } : {}),
  };
}

function minutosDesdeAhora(minutos: number): Date {
  return new Date(AHORA.getTime() + minutos * 60 * 1000);
}

function guardar(...bookings: GuestBooking[]): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(bookings));
}

function idsGuardados(): string[] {
  return JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]').map(
    (booking: GuestBooking) => booking.bookingId,
  );
}

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(AHORA);
  globalThis.localStorage = stubLocalStorage();
});

afterEach(() => {
  vi.useRealTimers();
});

describe('poda', () => {
  it('conserva el turno que terminó hace menos de una hora', () => {
    guardar(turno('recien', minutosDesdeAhora(-140), minutosDesdeAhora(-50)));

    expect(readGuestBookings()).toHaveLength(1);
  });

  it('olvida el turno que terminó hace más de una hora', () => {
    guardar(turno('viejo', minutosDesdeAhora(-160), minutosDesdeAhora(-70)));

    expect(readGuestBookings()).toEqual([]);
  });

  it('conserva el turno que todavía no se jugó', () => {
    guardar(turno('futuro', minutosDesdeAhora(60), minutosDesdeAhora(150)));

    expect(readGuestBookings()).toHaveLength(1);
  });

  it('mide cada turno contra el reloj, no contra su lugar en la lista', () => {
    // La primera versión hacía `.filter(sigueSirviendo)`, y filter le pasa a la
    // función (elemento, índice, array): el índice terminaba ocupando el lugar
    // del instante, así que el turno en la posición 0 se comparaba contra el
    // instante 0 y no vencía nunca. Por eso hay dos vencidos en posiciones
    // distintas, y uno vigente en el medio.
    guardar(
      turno('vencido-primero', minutosDesdeAhora(-300), minutosDesdeAhora(-200)),
      turno('vigente', minutosDesdeAhora(-100), minutosDesdeAhora(-10)),
      turno('vencido-tercero', minutosDesdeAhora(-400), minutosDesdeAhora(-300)),
    );

    expect(readGuestBookings().map((booking) => booking.bookingId)).toEqual(['vigente']);
  });

  it('deja la lista podada guardada, no sólo filtrada al leer', () => {
    guardar(
      turno('vencido', minutosDesdeAhora(-200), minutosDesdeAhora(-90)),
      turno('vigente', minutosDesdeAhora(-60), minutosDesdeAhora(-30)),
    );

    readGuestBookings();

    expect(idsGuardados()).toEqual(['vigente']);
  });

  it('no escribe cuando no hay nada que podar', () => {
    guardar(turno('vigente', minutosDesdeAhora(60), minutosDesdeAhora(150)));
    const escribir = vi.spyOn(localStorage, 'setItem');

    readGuestBookings();

    expect(escribir).not.toHaveBeenCalled();
  });
});

describe('entradas guardadas antes de que existiera la poda', () => {
  it('conserva la que no dice cuándo termina si el turno es reciente', () => {
    // Sin endTime se asume el turno más largo que el esquema permite, cuatro
    // horas, para no descartar uno que todavía podía servir.
    guardar(turno('sin-fin', minutosDesdeAhora(-30), null));

    expect(readGuestBookings()).toHaveLength(1);
  });

  it('olvida la que no dice cuándo termina cuando ya pasó el techo de cinco horas', () => {
    guardar(turno('sin-fin-viejo', minutosDesdeAhora(-301), null));

    expect(readGuestBookings()).toEqual([]);
  });

  it('descarta una entrada con fechas ilegibles en vez de arrastrarla para siempre', () => {
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify([{ bookingId: 'roto', startTime: 'no-es-una-fecha' }]),
    );

    expect(readGuestBookings()).toEqual([]);
  });
});

describe('alta y baja', () => {
  it('pone el turno nuevo primero', () => {
    rememberGuestBooking(turno('primero', minutosDesdeAhora(60), minutosDesdeAhora(150)));
    rememberGuestBooking(turno('segundo', minutosDesdeAhora(70), minutosDesdeAhora(160)));

    expect(idsGuardados()).toEqual(['segundo', 'primero']);
  });

  it('no duplica el mismo turno', () => {
    const mismo = turno('unico', minutosDesdeAhora(60), minutosDesdeAhora(150));
    rememberGuestBooking(mismo);
    rememberGuestBooking(mismo);

    expect(idsGuardados()).toEqual(['unico']);
  });

  it('olvida el turno cancelado', () => {
    rememberGuestBooking(turno('se-cancela', minutosDesdeAhora(60), minutosDesdeAhora(150)));
    rememberGuestBooking(turno('queda', minutosDesdeAhora(70), minutosDesdeAhora(160)));

    forgetGuestBooking('se-cancela');

    expect(idsGuardados()).toEqual(['queda']);
  });

  it('guarda a lo sumo veinte, descartando los más viejos', () => {
    for (let i = 0; i < 25; i++) {
      rememberGuestBooking(turno(`t${i}`, minutosDesdeAhora(60 + i), minutosDesdeAhora(150 + i)));
    }

    const guardados = idsGuardados();
    expect(guardados).toHaveLength(20);
    expect(guardados[0]).toBe('t24');
  });
});

describe('sin almacenamiento', () => {
  it('no rompe si el navegador lo tiene bloqueado', () => {
    // Modo privado o cuota llena: el turno sigue reservado igual, el jugador
    // sólo se queda sin el atajo para volver.
    globalThis.localStorage = {
      getItem: () => {
        throw new Error('bloqueado');
      },
      setItem: () => {
        throw new Error('bloqueado');
      },
    } as unknown as Storage;

    expect(readGuestBookings()).toEqual([]);
    expect(() =>
      rememberGuestBooking(turno('x', minutosDesdeAhora(60), minutosDesdeAhora(150))),
    ).not.toThrow();
  });
});

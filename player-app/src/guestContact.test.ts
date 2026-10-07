import { beforeEach, describe, expect, it } from 'vitest';
import { forgetGuestContact, readGuestContact, rememberGuestContact } from './guestContact';

/**
 * El nombre y teléfono de la última reserva sin cuenta.
 *
 * <p>Los instantes van explícitos en vez de mover el reloj: las funciones los
 * reciben, y así cada prueba dice en qué momento pasa cada cosa.
 */

const STORAGE_KEY = 'padel_guest_contact';
const AHORA = Date.parse('2026-10-07T20:00:00Z');
const DIA = 24 * 60 * 60 * 1000;

/** localStorage en memoria, como en guestBookings.test.ts: nada de esto toca el DOM. */
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

beforeEach(() => {
  globalThis.localStorage = stubLocalStorage();
});

describe('contacto del invitado', () => {
  it('devuelve lo que se guardó, sin espacios de más', () => {
    rememberGuestContact({ fullName: '  Juan Pérez ', phone: ' 2262 50-5703 ' }, AHORA);

    expect(readGuestContact(AHORA + DIA)).toEqual({ fullName: 'Juan Pérez', phone: '2262 50-5703' });
  });

  it('no guarda nada si falta el nombre o el teléfono', () => {
    rememberGuestContact({ fullName: '   ', phone: '2262 50-5703' }, AHORA);
    rememberGuestContact({ fullName: 'Juan', phone: '' }, AHORA);

    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('la última reserva reemplaza a la anterior', () => {
    rememberGuestContact({ fullName: 'Juan', phone: '2262 50-5703' }, AHORA);
    rememberGuestContact({ fullName: 'Ana', phone: '2262 11-2233' }, AHORA + DIA);

    expect(readGuestContact(AHORA + 2 * DIA)).toEqual({ fullName: 'Ana', phone: '2262 11-2233' });
  });

  it('vence a los seis meses sin reservar, y se borra', () => {
    rememberGuestContact({ fullName: 'Juan', phone: '2262 50-5703' }, AHORA);

    expect(readGuestContact(AHORA + 179 * DIA)).not.toBeNull();
    expect(readGuestContact(AHORA + 180 * DIA)).toBeNull();
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('cada reserva renueva el plazo', () => {
    rememberGuestContact({ fullName: 'Juan', phone: '2262 50-5703' }, AHORA);
    rememberGuestContact({ fullName: 'Juan', phone: '2262 50-5703' }, AHORA + 100 * DIA);

    expect(readGuestContact(AHORA + 250 * DIA)).not.toBeNull();
  });

  it('descarta lo ilegible en vez de romper el checkout', () => {
    localStorage.setItem(STORAGE_KEY, '{no es json');
    expect(readGuestContact(AHORA)).toBeNull();

    localStorage.setItem(STORAGE_KEY, JSON.stringify({ fullName: 'Juan', phone: 2262 }));
    expect(readGuestContact(AHORA)).toBeNull();
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('"No soy yo" lo olvida', () => {
    rememberGuestContact({ fullName: 'Juan', phone: '2262 50-5703' }, AHORA);

    forgetGuestContact();

    expect(readGuestContact(AHORA)).toBeNull();
  });

  it('sin localStorage no rompe nada', () => {
    globalThis.localStorage = {
      getItem: () => {
        throw new Error('bloqueado');
      },
      setItem: () => {
        throw new Error('bloqueado');
      },
      removeItem: () => {
        throw new Error('bloqueado');
      },
    } as unknown as Storage;

    expect(() => rememberGuestContact({ fullName: 'Juan', phone: '2262 50-5703' }, AHORA)).not.toThrow();
    expect(readGuestContact(AHORA)).toBeNull();
    expect(() => forgetGuestContact()).not.toThrow();
  });
});

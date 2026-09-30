import { describe, expect, it } from 'vitest';
import { PHONE_MISSING, PHONE_NEEDS_AREA_CODE, phoneProblem } from './phone';

/**
 * El control del checkout antes de mandar la reserva.
 *
 * <p>Lo que importa es que no rechace ningún formato que el servidor acepta: un
 * falso rechazo acá deja al jugador sin poder reservar. Los casos válidos son los
 * mismos que acepta `PhoneNumbers` del lado del servidor.
 */
describe('phoneProblem', () => {
  it('pide el teléfono si está vacío', () => {
    expect(phoneProblem('')).toBe(PHONE_MISSING);
    expect(phoneProblem('   ')).toBe(PHONE_MISSING);
  });

  it('acepta los formatos argentinos de todo el país', () => {
    for (const phone of [
      '2262 15-415000',
      '02262 15 415000',
      '2262415000',
      '(2262) 415000',
      '11 5555 1234',
      '011 15 5555 1234',
      '351 15 555 1234',
      '379 15 441 2345',
      '2901 15 55 1234',
      '549 2262 415000',
      '+54 9 2262 415000',
      '0054 9 2262 415000',
    ]) {
      expect(phoneProblem(phone), phone).toBeNull();
    }
  });

  it('deja pasar los números del exterior: los valida el servidor', () => {
    expect(phoneProblem('+598 99 123 456')).toBeNull();
    expect(phoneProblem('+55 11 91234 5678')).toBeNull();
  });

  it('avisa que falta el código de área', () => {
    for (const phone of ['15415000', '15 415000', '415000', '15 5555 1234', '0 15 415000']) {
      expect(phoneProblem(phone), phone).toBe(PHONE_NEEDS_AREA_CODE);
    }
  });
});

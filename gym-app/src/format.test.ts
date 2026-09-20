import { describe, expect, it } from 'vitest';
import { digitsOnly, firstName, longDay, shortDay, weekUsage } from './format';

describe('format', () => {
  it('muestra los dias de calendario sin correrlos por la zona horaria', () => {
    expect(shortDay('2026-09-14')).toBe('14/09');
    expect(longDay('2026-01-05')).toBe('05/01/2026');
  });

  it('deja solo los digitos del DNI', () => {
    expect(digitsOnly('30.111.222')).toBe('30111222');
    expect(digitsOnly(' 30 111 222 ')).toBe('30111222');
  });

  it('toma el primer nombre', () => {
    expect(firstName('Ana Gómez')).toBe('Ana');
    expect(firstName('  Beto  ')).toBe('Beto');
    expect(firstName('')).toBe('');
  });

  it('dice cuantos dias de la semana uso, en singular cuando corresponde', () => {
    expect(weekUsage(2, 3)).toBe('2 de 3 días');
    expect(weekUsage(0, 1)).toBe('0 de 1 día');
  });
});

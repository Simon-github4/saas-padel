import { describe, expect, it } from 'vitest';
import { digitsOnly, firstName, longDay, shortDay, usage, usageTitle, usageUntil } from './format';

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
    expect(usage(2, 3, 'WEEK')).toBe('2 de 3 días');
    expect(usage(0, 1, 'WEEK')).toBe('0 de 1 día');
    expect(usageTitle('WEEK')).toBe('Esta semana');
    expect(usageUntil('WEEK', '2026-10-25')).toBeNull();
  });

  it('en el mes y en el pase cuenta ingresos y dice hasta cuando', () => {
    expect(usage(3, 9, 'MONTH')).toBe('3 de 9 ingresos');
    expect(usageTitle('MONTH')).toBe('Este mes');
    expect(usageUntil('MONTH', '2026-11-14')).toBe('Tu mes va hasta el 14/11');
    expect(usage(4, 4, 'PASS')).toBe('4 de 4 ingresos');
    expect(usageTitle('PASS')).toBe('Este pase');
    expect(usageUntil('PASS', '2026-11-04')).toBe('Tu pase vale hasta el 04/11');
  });
});

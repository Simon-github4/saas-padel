import { describe, expect, it } from 'vitest';
import { describeProblem, problemFromCode } from './geolocation';

describe('geolocation', () => {
  it('traduce los códigos del navegador a un motivo', () => {
    expect(problemFromCode(1)).toBe('denied');
    expect(problemFromCode(2)).toBe('unavailable');
    expect(problemFromCode(3)).toBe('timeout');
    expect(problemFromCode(99)).toBe('unavailable');
  });

  it('cada motivo tiene un remedio distinto para el jugador', () => {
    const messages = (['denied', 'unavailable', 'timeout', 'unsupported'] as const).map(describeProblem);

    expect(new Set(messages).size).toBe(4);
    expect(describeProblem('denied')).toContain('permiso');
  });
});

import { describe, expect, it } from 'vitest';
import type { Me, Membership } from './api/gymClient';
import { passState } from './billing';

const month: Membership = {
  startsOn: '2026-10-01',
  endsOn: '2026-10-31',
  daysPerWeek: 3,
  sedes: ['Necochea'],
  planLength: 'MONTH',
};
const pass: Membership = { ...month, startsOn: '2026-10-21', endsOn: '2026-11-04', planLength: 'FIFTEEN_DAYS' };

function me(overrides: Partial<Me>): Me {
  return {
    fullName: 'Tomás Paso',
    membership: month,
    valid: true,
    weekUsed: 0,
    weekLimit: 3,
    checkedInToday: false,
    recent: [],
    cycleStart: '2026-10-01',
    periodStart: '2026-10-01',
    periodEnd: '2026-10-31',
    paidUntil: '2026-10-31',
    paidCurrent: true,
    monthsLate: 0,
    canEnter: true,
    owedTotal: 0,
    onPasses: false,
    gap: null,
    paidAhead: null,
    ...overrides,
  };
}

describe('passState', () => {
  it('no se mete con el plan mensual: ni al día, ni con gracia, ni bloqueado', () => {
    expect(passState(me({}))).toBeNull();
    expect(passState(me({ paidCurrent: false, monthsLate: 1, paidUntil: null }))).toBeNull();
    expect(passState(me({ monthsLate: 3, canEnter: false, paidUntil: null }))).toBeNull();
    expect(passState(me({ membership: null, canEnter: false }))).toBeNull();
  });

  it('con un pase que lo cubre hoy, está vigente', () => {
    expect(passState(me({ membership: pass, onPasses: true, paidUntil: '2026-11-04' }))).toBe('vigente');
  });

  it('un pase que corre con el mensual por empezar también está vigente', () => {
    expect(passState(me({ membership: pass, onPasses: false, paidUntil: '2026-12-04' }))).toBe('vigente');
  });

  it('un pase que todavía no empezó está por empezar', () => {
    expect(
      passState(me({ membership: pass, onPasses: true, canEnter: false, paidUntil: '2026-11-04' })),
    ).toBe('por-empezar');
  });

  it('un pase vencido no es deuda: está vencido', () => {
    expect(passState(me({ membership: pass, onPasses: true, canEnter: false, paidUntil: null }))).toBe('vencido');
  });

  it('con pases y un mes que todavía corre, manda la cobertura', () => {
    expect(passState(me({ onPasses: true, paidUntil: '2026-11-15' }))).toBe('vigente');
  });
});

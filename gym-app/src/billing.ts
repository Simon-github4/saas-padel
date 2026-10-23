import type { Me } from './api/gymClient';

/** Cómo está la cuota del que se rige por un pase de 15 días. */
export type PassState = 'vigente' | 'por-empezar' | 'vencido';

/**
 * El estado de la cuota cuando lo deciden las reglas del pase (solo la cobertura, sin deuda
 * ni gracia): el socio está con pases, o un pase lo cubre hoy con el plan mensual por empezar.
 * Null si sigue el plan mensual: ahí mandan la deuda y la gracia de siempre.
 */
export function passState(me: Me): PassState | null {
  if (!me.membership || (!me.onPasses && me.membership.planLength !== 'FIFTEEN_DAYS')) {
    return null;
  }
  if (me.canEnter) {
    return 'vigente';
  }
  // Sin poder entrar, el servidor manda hasta cuándo llega lo pago solo si todavía no empezó.
  return me.paidUntil ? 'por-empezar' : 'vencido';
}

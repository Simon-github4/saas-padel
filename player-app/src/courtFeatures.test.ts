import { describe, expect, it } from 'vitest';
import {
  courtMatches,
  featuresSummary,
  roofFromParam,
  roofParam,
  surfaceFromParam,
  surfaceParam,
  wallFromParam,
  wallParam,
} from './courtFeatures';

describe('featuresSummary', () => {
  it('nombra techo y pared cuando todas las canchas libres son iguales', () => {
    expect(featuresSummary(['GLASS'], ['CARPET'], ['COVERED'])).toEqual(['Techada', 'Blindex']);
    expect(featuresSummary(['WALL'], ['CARPET'], ['OUTDOOR'])).toEqual(['Al aire libre', 'Pared']);
  });

  it('junta los dos valores cuando hay canchas distintas', () => {
    expect(featuresSummary(['WALL', 'GLASS'], ['CARPET'], ['OUTDOOR', 'COVERED'])).toEqual([
      'Techada y al aire libre',
      'Blindex y pared',
    ]);
  });

  it('solo menciona el piso si alguna cancha no tiene alfombra', () => {
    expect(featuresSummary(['WALL'], ['NO_CARPET'], ['OUTDOOR'])).toEqual(['Al aire libre', 'Pared', 'Sin alfombra']);
    expect(featuresSummary(['GLASS', 'WALL'], ['CARPET', 'NO_CARPET'], ['COVERED'])).toEqual([
      'Techada',
      'Blindex y pared',
      'Con y sin alfombra',
    ]);
  });
});

describe('parámetros de la URL', () => {
  it('ida y vuelta en castellano', () => {
    expect(wallFromParam(wallParam('GLASS'))).toBe('GLASS');
    expect(wallFromParam(wallParam('WALL'))).toBe('WALL');
    expect(surfaceFromParam(surfaceParam('NO_CARPET'))).toBe('NO_CARPET');
    expect(roofFromParam(roofParam('COVERED'))).toBe('COVERED');
    expect(roofFromParam(roofParam('OUTDOOR'))).toBe('OUTDOOR');
  });

  it('un valor desconocido o ausente es "me da igual"', () => {
    expect(wallFromParam('madera')).toBeNull();
    expect(wallFromParam(null)).toBeNull();
    expect(surfaceFromParam('')).toBeNull();
    expect(roofFromParam('cubierta')).toBeNull();
  });
});

describe('courtMatches', () => {
  const pared = { wall: 'WALL' as const, surface: 'NO_CARPET' as const, roof: 'OUTDOOR' as const };

  it('sin preferencias acepta cualquier cancha', () => {
    expect(courtMatches(pared, null, null, null)).toBe(true);
  });

  it('la cancha tiene que cumplir todo lo pedido', () => {
    expect(courtMatches(pared, 'WALL', null, null)).toBe(true);
    expect(courtMatches(pared, 'WALL', 'NO_CARPET', 'OUTDOOR')).toBe(true);
    expect(courtMatches(pared, 'WALL', 'CARPET', null)).toBe(false);
    expect(courtMatches(pared, 'WALL', null, 'COVERED')).toBe(false);
  });
});

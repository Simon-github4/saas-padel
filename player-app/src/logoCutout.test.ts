import { describe, expect, it } from 'vitest';
import { cutWhiteBackground } from './logoCutout';

/** Imagen de prueba: cada fila es un string, w = blanco, k = negro, b = azul. */
function image(rows: string[]): Uint8ClampedArray {
  const colors: Record<string, number[]> = { w: [255, 255, 255], k: [0, 0, 0], b: [20, 60, 200] };
  return new Uint8ClampedArray(rows.flatMap((row) => [...row].flatMap((c) => [...colors[c], 255])));
}

function alphaAt(data: Uint8ClampedArray, width: number, x: number, y: number): number {
  return data[(y * width + x) * 4 + 3];
}

describe('cutWhiteBackground', () => {
  it('borra el blanco del borde pero no el que está encerrado por el contorno', () => {
    const data = image(['wwwwwww', 'wkkkkkw', 'wkwwwkw', 'wkkkkkw', 'wwwwwww']);
    const result = cutWhiteBackground(data, 7, 5);
    expect(result.cut).toBe(true);
    expect(alphaAt(data, 7, 0, 0)).toBe(0);
    expect(alphaAt(data, 7, 1, 1)).toBe(255);
    expect(alphaAt(data, 7, 3, 2)).toBe(255);
  });

  it('no borra el relleno de una letra que el borde de la imagen corta', () => {
    const rows = Array.from({ length: 12 }, () => 'w'.repeat(12));
    rows[4] = 'wwwwwwwwkkkk';
    rows[5] = 'wwwwwwwwkwww';
    rows[6] = 'wwwwwwwwkkkk';
    const data = image(rows);
    cutWhiteBackground(data, 12, 12);
    expect(alphaAt(data, 12, 0, 0)).toBe(0);
    expect(alphaAt(data, 12, 11, 5)).toBe(255);
  });

  it('no recorta un logo que no está sobre blanco', () => {
    const data = image(['bbbbb', 'bwwwb', 'bbbbb']);
    const result = cutWhiteBackground(data, 5, 3);
    expect(result.cut).toBe(false);
    expect(alphaAt(data, 5, 2, 1)).toBe(255);
  });

  it('toma el color de marca del logo', () => {
    const data = image(['wwwww', 'wbbbw', 'wbbbw', 'wwwww']);
    expect(cutWhiteBackground(data, 5, 4).tint).toEqual([20, 60, 200]);
  });
});

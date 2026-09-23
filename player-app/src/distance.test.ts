import { describe, expect, it } from 'vitest';
import { distanceKm, formatDistance } from './distance';

describe('distanceKm', () => {
  it('da cero para el mismo punto', () => {
    const point = { latitude: -38.5573, longitude: -58.7301 };
    expect(distanceKm(point, point)).toBe(0);
  });

  it('mide Necochea–Quequén como unos pocos km, sin importar el sentido', () => {
    const necochea = { latitude: -38.5545, longitude: -58.7396 };
    const quequen = { latitude: -38.5302, longitude: -58.7019 };

    const ida = distanceKm(necochea, quequen);

    expect(ida).toBeGreaterThan(3.5);
    expect(ida).toBeLessThan(5);
    expect(distanceKm(quequen, necochea)).toBeCloseTo(ida, 10);
  });

  it('un grado de latitud son unos 111 km', () => {
    expect(distanceKm({ latitude: -38, longitude: -58 }, { latitude: -39, longitude: -58 })).toBeCloseTo(111.2, 0);
  });
});

describe('formatDistance', () => {
  it('por debajo del km, en metros redondeados a 50', () => {
    expect(formatDistance(0.837)).toBe('a 850 m');
    expect(formatDistance(0.12)).toBe('a 100 m');
  });

  it('muy cerca no dice "a 0 m"', () => {
    expect(formatDistance(0)).toBe('a 50 m');
  });

  it('casi un km redondea a "a 1 km", no a "a 1000 m"', () => {
    expect(formatDistance(0.99)).toBe('a 1 km');
  });

  it('en km, con un decimal y coma hasta 10 km, y entero después', () => {
    expect(formatDistance(2.34)).toBe('a 2,3 km');
    expect(formatDistance(3)).toBe('a 3 km');
    expect(formatDistance(12.6)).toBe('a 13 km');
  });
});

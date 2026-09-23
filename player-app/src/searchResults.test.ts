import { describe, expect, it } from 'vitest';
import type { ClubOption, SearchMatch } from './api/client';
import { clubFeatures, clubInitials, groupByClub, slotMinutes, sortByDistance } from './searchResults';

function match(clubSlug: string, startTime: string, overrides: Partial<SearchMatch> = {}): SearchMatch {
  return {
    clubSlug,
    clubName: `Club ${clubSlug}`,
    city: 'Necochea, Buenos Aires',
    startTime,
    endTime: '23:59',
    startsAt: `2026-09-15T${startTime}:00-03:00`,
    cheapestPrice: 36000,
    playersPerCourt: 4,
    freeCourts: 1,
    promo: false,
    walls: ['GLASS'],
    surfaces: ['CARPET'],
    roofs: ['OUTDOOR'],
    ...overrides,
  };
}

function club(
  slug: string,
  heroImageUrl: string | null,
  location: { latitude: number | null; longitude: number | null } = { latitude: null, longitude: null },
): ClubOption {
  return { slug, name: `Club ${slug}`, city: 'Necochea, Buenos Aires', bookingHorizonDays: 21, heroImageUrl, ...location };
}

// Puntos reales de Necochea, para que las distancias sean creíbles.
const PLAZA = { latitude: -38.5545, longitude: -58.7396 };
const CERCA = { latitude: -38.5573, longitude: -58.7301 }; // ~900 m de la plaza
const LEJOS = { latitude: -38.5760, longitude: -58.7010 }; // ~4 km, hacia el puerto

describe('groupByClub', () => {
  it('a igual cantidad de horarios, ordena por el primer turno y respeta el orden de los horarios adentro', () => {
    const groups = groupByClub(
      [match('muelle', '18:00'), match('costa', '18:30'), match('muelle', '19:30'), match('costa', '20:00')],
      [club('costa', null), club('muelle', null)],
    );

    expect(groups.map((group) => group.slug)).toEqual(['muelle', 'costa']);
    expect(groups[0].matches.map((item) => item.startTime)).toEqual(['18:00', '19:30']);
    expect(groups[1].matches.map((item) => item.startTime)).toEqual(['18:30', '20:00']);
  });

  it('arriba el club con más horarios distintos, aunque otro tenga el primer turno', () => {
    const groups = groupByClub(
      [match('temprano', '17:00'), match('muchos', '18:00'), match('muchos', '19:00'), match('muchos', '20:00')],
      [club('temprano', null), club('muchos', null)],
    );

    expect(groups.map((group) => group.slug)).toEqual(['muchos', 'temprano']);
  });

  it('cuenta horarios y no canchas: muchas canchas a la misma hora son una sola opción', () => {
    const groups = groupByClub(
      [
        match('canchas', '18:00', { freeCourts: 6 }),
        match('horarios', '19:00'),
        match('horarios', '20:00'),
      ],
      [club('canchas', null), club('horarios', null)],
    );

    expect(groups.map((group) => group.slug)).toEqual(['horarios', 'canchas']);
  });

  it('toma la foto del listado de clubes, y null si el club no está o no cargó una', () => {
    const groups = groupByClub(
      [match('costa', '18:00'), match('muelle', '19:00'), match('nuevo', '20:00')],
      [club('costa', '/api/public/costa/hero-image'), club('muelle', null)],
    );

    expect(groups.map((group) => group.heroImageUrl)).toEqual(['/api/public/costa/hero-image', null, null]);
  });

  it('sin turnos no arma tarjetas', () => {
    expect(groupByClub([], [club('costa', null)])).toEqual([]);
  });

  it('toma la ubicación del listado, y null si falta una de las dos coordenadas', () => {
    const groups = groupByClub(
      [match('costa', '18:00'), match('muelle', '19:00')],
      [club('costa', null, CERCA), club('muelle', null, { latitude: -38.5, longitude: null })],
    );

    expect(groups.map((group) => group.location)).toEqual([CERCA, null]);
  });
});

describe('sortByDistance', () => {
  it('ordena de más cerca a más lejos, aunque el lejano tenga el primer turno', () => {
    const groups = groupByClub(
      [match('lejos', '18:00'), match('cerca', '21:00')],
      [club('lejos', null, LEJOS), club('cerca', null, CERCA)],
    );

    const sorted = sortByDistance(groups, PLAZA);

    expect(sorted.map((group) => group.slug)).toEqual(['cerca', 'lejos']);
    expect(sorted[0].distanceKm).toBeGreaterThan(0.5);
    expect(sorted[0].distanceKm).toBeLessThan(1.5);
    expect(sorted[1].distanceKm).toBeGreaterThan(3);
  });

  it('los clubes sin ubicación van al final, sin distancia y en el orden por defecto', () => {
    const groups = groupByClub(
      [match('sin-a', '17:00'), match('lejos', '18:00'), match('sin-b', '19:00'), match('cerca', '20:00')],
      [club('sin-a', null), club('lejos', null, LEJOS), club('sin-b', null), club('cerca', null, CERCA)],
    );

    const sorted = sortByDistance(groups, PLAZA);

    expect(sorted.map((group) => group.slug)).toEqual(['cerca', 'lejos', 'sin-a', 'sin-b']);
    expect(sorted.slice(2).map((group) => group.distanceKm)).toEqual([null, null]);
  });

  it('a igual distancia manda el orden por defecto: más horarios primero', () => {
    const groups = groupByClub(
      [match('temprano', '18:00'), match('muchos', '19:00'), match('muchos', '20:00')],
      [club('muchos', null, CERCA), club('temprano', null, CERCA)],
    );

    expect(sortByDistance(groups, PLAZA).map((group) => group.slug)).toEqual(['muchos', 'temprano']);
  });
});

describe('clubInitials', () => {
  it('usa la primera letra de las dos primeras palabras', () => {
    expect(clubInitials('SIMON PADEL')).toBe('SP');
    expect(clubInitials('Pádel  Necochea Club')).toBe('PN');
  });

  it('con una sola palabra, sus dos primeras letras en mayúscula', () => {
    expect(clubInitials('quequén')).toBe('QU');
  });

  it('un nombre vacío no rompe', () => {
    expect(clubInitials('   ')).toBe('');
  });
});

describe('slotMinutes', () => {
  it('devuelve la duración cuando todos los turnos duran lo mismo', () => {
    expect(
      slotMinutes([
        { startTime: '18:00', endTime: '19:30' },
        { startTime: '19:30', endTime: '21:00' },
      ]),
    ).toBe(90);
  });

  it('cuenta bien un turno que cruza la medianoche', () => {
    expect(slotMinutes([{ startTime: '23:00', endTime: '00:30' }])).toBe(90);
  });

  it('con duraciones distintas no afirma ninguna', () => {
    expect(
      slotMinutes([
        { startTime: '18:00', endTime: '19:00' },
        { startTime: '19:00', endTime: '20:30' },
      ]),
    ).toBeNull();
  });
});

describe('clubFeatures', () => {
  it('junta las canchas de todos los horarios sin repetir', () => {
    const features = clubFeatures([
      match('costa', '18:00', { walls: ['GLASS'], roofs: ['COVERED'] }),
      match('costa', '19:30', { walls: ['GLASS', 'WALL'], surfaces: ['NO_CARPET'], roofs: ['COVERED'] }),
    ]);

    expect(features.walls).toEqual(['GLASS', 'WALL']);
    expect(features.surfaces).toEqual(['CARPET', 'NO_CARPET']);
    expect(features.roofs).toEqual(['COVERED']);
  });
});

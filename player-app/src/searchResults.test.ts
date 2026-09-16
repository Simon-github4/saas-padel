import { describe, expect, it } from 'vitest';
import type { ClubOption, SearchMatch } from './api/client';
import { clubFeatures, clubInitials, groupByClub, slotMinutes } from './searchResults';

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

function club(slug: string, heroImageUrl: string | null): ClubOption {
  return { slug, name: `Club ${slug}`, city: 'Necochea, Buenos Aires', bookingHorizonDays: 21, heroImageUrl };
}

describe('groupByClub', () => {
  it('ordena los clubes por su primer turno y respeta el orden de los horarios adentro', () => {
    const groups = groupByClub(
      [match('muelle', '18:00'), match('costa', '18:30'), match('muelle', '19:30'), match('costa', '20:00')],
      [club('costa', null), club('muelle', null)],
    );

    expect(groups.map((group) => group.slug)).toEqual(['muelle', 'costa']);
    expect(groups[0].matches.map((item) => item.startTime)).toEqual(['18:00', '19:30']);
    expect(groups[1].matches.map((item) => item.startTime)).toEqual(['18:30', '20:00']);
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

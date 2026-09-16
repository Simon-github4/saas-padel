import type { ClubOption, CourtRoof, CourtSurface, CourtWall, SearchMatch } from './api/client';

/** Los turnos libres de un club, para su tarjeta en la búsqueda. */
export interface ClubResults {
  slug: string;
  name: string;
  city: string | null;
  /** Foto de portada del club, o null si no cargó ninguna. */
  heroImageUrl: string | null;
  /** En el orden por horario en que llegan de la búsqueda. */
  matches: SearchMatch[];
}

/**
 * Agrupa por club los turnos que la búsqueda devuelve ordenados por horario.
 *
 * <p>Los clubes quedan en el orden de su primer turno: arriba el que permite jugar
 * más temprano, que es lo primero que quiere saber quien busca "donde sea". La foto
 * sale del listado de clubes, que viaja una sola vez en vez de repetirse en cada turno.
 */
export function groupByClub(matches: SearchMatch[], clubs: ClubOption[]): ClubResults[] {
  const photos = new Map(clubs.map((club) => [club.slug, club.heroImageUrl]));
  const bySlug = new Map<string, ClubResults>();
  for (const match of matches) {
    let group = bySlug.get(match.clubSlug);
    if (!group) {
      group = {
        slug: match.clubSlug,
        name: match.clubName,
        city: match.city,
        heroImageUrl: photos.get(match.clubSlug) ?? null,
        matches: [],
      };
      bySlug.set(match.clubSlug, group);
    }
    group.matches.push(match);
  }
  return [...bySlug.values()];
}

/**
 * Lo que va en lugar de la foto si el club no cargó una: "SIMON PADEL" → "SP",
 * "Quequén" → "QU".
 */
export function clubInitials(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) {
    return '';
  }
  const letters = words.length === 1 ? words[0].slice(0, 2) : words[0][0] + words[1][0];
  return letters.toLocaleUpperCase('es-AR');
}

/**
 * Cuánto duran los turnos del club, si todos duran lo mismo; null si hay de
 * distintas duraciones, para no afirmar en la tarjeta algo que vale solo para
 * algunos. Un turno que cruza la medianoche ("23:00" a "00:30") dura 90, no -1350.
 */
export function slotMinutes(matches: Pick<SearchMatch, 'startTime' | 'endTime'>[]): number | null {
  const durations = new Set(
    matches.map((match) => {
      const start = toMinutes(match.startTime);
      const end = toMinutes(match.endTime);
      return end > start ? end - start : end + 24 * 60 - start;
    }),
  );
  return durations.size === 1 ? [...durations][0] : null;
}

/** Paredes, pisos y techos de todas las canchas libres del club en el rango, sin repetir. */
export function clubFeatures(matches: SearchMatch[]): {
  walls: CourtWall[];
  surfaces: CourtSurface[];
  roofs: CourtRoof[];
} {
  return {
    walls: [...new Set(matches.flatMap((match) => match.walls))],
    surfaces: [...new Set(matches.flatMap((match) => match.surfaces))],
    roofs: [...new Set(matches.flatMap((match) => match.roofs))],
  };
}

function toMinutes(time: string): number {
  const [hours, minutes] = time.split(':').map(Number);
  return hours * 60 + minutes;
}

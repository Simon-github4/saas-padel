import type { ClubOption, CourtRoof, CourtSurface, CourtWall, SearchMatch } from './api/client';
import { type Coordinates, distanceKm } from './distance';

/** Los turnos libres de un club, para su tarjeta en la búsqueda. */
export interface ClubResults {
  slug: string;
  name: string;
  city: string | null;
  /** Foto de portada del club, o null si no cargó ninguna. */
  heroImageUrl: string | null;
  /** Ubicación del club, o null si no la cargó: sin ella no se puede ordenar por cercanía. */
  location: Coordinates | null;
  /** En el orden por horario en que llegan de la búsqueda. */
  matches: SearchMatch[];
}

/** Una tarjeta en el orden por cercanía: la distancia es null si el club no cargó su ubicación. */
export interface ClubByDistance extends ClubResults {
  distanceKm: number | null;
}

/**
 * Agrupa por club los turnos que la búsqueda devuelve ordenados por horario.
 *
 * <p>Arriba queda el club con más horarios distintos libres: es el que más chances
 * le da de encontrar uno que le sirva a quien busca "donde sea". Cuenta horarios y
 * no canchas: tres canchas libres a las 20:00 siguen siendo una sola opción de hora.
 * A igual cantidad, el que permite jugar más temprano. La foto sale del listado de
 * clubes, que viaja una sola vez en vez de repetirse en cada turno.
 */
export function groupByClub(matches: SearchMatch[], clubs: ClubOption[]): ClubResults[] {
  const bySlugOption = new Map(clubs.map((club) => [club.slug, club]));
  const bySlug = new Map<string, ClubResults>();
  for (const match of matches) {
    let group = bySlug.get(match.clubSlug);
    if (!group) {
      group = {
        slug: match.clubSlug,
        name: match.clubName,
        city: match.city,
        heroImageUrl: bySlugOption.get(match.clubSlug)?.heroImageUrl ?? null,
        location: locationOf(bySlugOption.get(match.clubSlug)),
        matches: [],
      };
      bySlug.set(match.clubSlug, group);
    }
    group.matches.push(match);
  }
  // Los grupos nacen en el orden de su primer turno (los turnos llegan por horario),
  // y el sort es estable: ese orden queda como desempate sin escribirlo.
  return [...bySlug.values()].sort((a, b) => distinctTimes(b) - distinctTimes(a));
}

/** Cuántas horas de inicio distintas tiene libres el club, sin importar cuántas canchas en cada una. */
function distinctTimes(group: ClubResults): number {
  return new Set(group.matches.map((match) => match.startTime)).size;
}

/**
 * Las tarjetas de más cerca a más lejos del jugador. Los clubes sin ubicación
 * cargada van al final, sin distancia: no se sabe dónde quedan, pero siguen
 * teniendo turnos libres.
 *
 * <p>A igual distancia -y entre los que no tienen ubicación- queda el orden en
 * que llegan de {@link groupByClub}, el de más horarios: el sort es estable.
 */
export function sortByDistance(groups: ClubResults[], origin: Coordinates): ClubByDistance[] {
  return groups
    .map((group) => ({
      ...group,
      distanceKm: group.location ? distanceKm(origin, group.location) : null,
    }))
    .sort((a, b) => {
      if (a.distanceKm === null || b.distanceKm === null) {
        return (a.distanceKm === null ? 1 : 0) - (b.distanceKm === null ? 1 : 0);
      }
      return a.distanceKm - b.distanceKm;
    });
}

/** Las dos coordenadas o ninguna: media no ubica al club. */
function locationOf(club: ClubOption | undefined): Coordinates | null {
  if (!club || typeof club.latitude !== 'number' || typeof club.longitude !== 'number') {
    return null;
  }
  return { latitude: club.latitude, longitude: club.longitude };
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

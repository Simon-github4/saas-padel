import type { CourtRoof, CourtSurface, CourtWall } from './api/client';

/**
 * Paredes, piso y techo de una cancha, dichos como los dice el jugador.
 *
 * <p>En la URL de la búsqueda y del club van en castellano (?paredes=blindex&piso=sin-alfombra&techo=techada):
 * son links que se comparten por WhatsApp y se leen. La API usa los valores del enum.
 */

export const WALL_LABEL: Record<CourtWall, string> = { GLASS: 'Blindex', WALL: 'Pared' };
export const SURFACE_LABEL: Record<CourtSurface, string> = { CARPET: 'Alfombra', NO_CARPET: 'Sin alfombra' };
export const ROOF_LABEL: Record<CourtRoof, string> = { COVERED: 'Techada', OUTDOOR: 'Al aire libre' };

const WALL_PARAMS: Record<CourtWall, string> = { GLASS: 'blindex', WALL: 'pared' };
const SURFACE_PARAMS: Record<CourtSurface, string> = { CARPET: 'alfombra', NO_CARPET: 'sin-alfombra' };
const ROOF_PARAMS: Record<CourtRoof, string> = { COVERED: 'techada', OUTDOOR: 'aire-libre' };

export function wallParam(wall: CourtWall): string {
  return WALL_PARAMS[wall];
}

export function surfaceParam(surface: CourtSurface): string {
  return SURFACE_PARAMS[surface];
}

export function roofParam(roof: CourtRoof): string {
  return ROOF_PARAMS[roof];
}

/** El valor de ?paredes=; cualquier otra cosa es "me da igual". */
export function wallFromParam(value: string | null): CourtWall | null {
  return (Object.keys(WALL_PARAMS) as CourtWall[]).find((wall) => WALL_PARAMS[wall] === value) ?? null;
}

/** El valor de ?piso=; cualquier otra cosa es "me da igual". */
export function surfaceFromParam(value: string | null): CourtSurface | null {
  return (Object.keys(SURFACE_PARAMS) as CourtSurface[]).find((surface) => SURFACE_PARAMS[surface] === value) ?? null;
}

/** El valor de ?techo=; cualquier otra cosa es "me da igual". */
export function roofFromParam(value: string | null): CourtRoof | null {
  return (Object.keys(ROOF_PARAMS) as CourtRoof[]).find((roof) => ROOF_PARAMS[roof] === value) ?? null;
}

/**
 * Cómo son las canchas libres de un turno, en pocas palabras: primero el techo
 * ("Techada", "Techada y al aire libre"), que es lo que se mira si llueve; después
 * las paredes ("Blindex", "Blindex y pared"), y el piso solo si hay alguna sin
 * alfombra, que es la excepción. Decir "alfombra" en cada tarjeta sería ruido: es
 * lo que se da por hecho.
 */
export function featuresSummary(walls: CourtWall[], surfaces: CourtSurface[], roofs: CourtRoof[]): string[] {
  const parts: string[] = [];
  if (roofs.includes('COVERED') && roofs.includes('OUTDOOR')) {
    parts.push('Techada y al aire libre');
  } else if (roofs.length === 1) {
    parts.push(ROOF_LABEL[roofs[0]]);
  }
  if (walls.includes('GLASS') && walls.includes('WALL')) {
    parts.push('Blindex y pared');
  } else if (walls.length === 1) {
    parts.push(WALL_LABEL[walls[0]]);
  }
  if (surfaces.includes('NO_CARPET')) {
    parts.push(surfaces.includes('CARPET') ? 'Con y sin alfombra' : 'Sin alfombra');
  }
  return parts;
}

/** Si la cancha cumple lo que pidió el jugador; null en un campo es "me da igual". */
export function courtMatches(
  court: { wall: CourtWall; surface: CourtSurface; roof: CourtRoof },
  wall: CourtWall | null,
  surface: CourtSurface | null,
  roof: CourtRoof | null,
): boolean {
  return (!wall || court.wall === wall) && (!surface || court.surface === surface) && (!roof || court.roof === roof);
}

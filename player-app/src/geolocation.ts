/**
 * La ubicación del jugador, para ordenar la búsqueda por cercanía.
 *
 * <p>Se usa solo en el navegador: la distancia a cada club se calcula acá y la
 * posición nunca viaja al servidor. Por eso tampoco se guarda en el
 * almacenamiento del navegador: queda en memoria mientras la pestaña está
 * abierta, lo justo para no volver a pedirla cada vez que el jugador entra a
 * un club y vuelve a la lista.
 *
 * <p>Alcanza con una ubicación aproximada -se compara "a 800 m" contra "a 4 km"-,
 * así que se pide sin alta precisión: responde más rápido y sin esperar al GPS.
 */

export interface Position {
  latitude: number;
  longitude: number;
}

/** Por qué no se pudo obtener: cada motivo tiene un remedio distinto para el jugador. */
export type GeoProblem = 'denied' | 'unavailable' | 'timeout' | 'unsupported';

export class GeoError extends Error {
  constructor(readonly problem: GeoProblem) {
    super(problem);
  }
}

const TIMEOUT_MS = 10_000;
/** Una lectura de hace menos de esto sirve: nadie cruza la ciudad buscando cancha. */
const MAX_AGE_MS = 10 * 60_000;

let remembered: Position | null = null;

/** Los códigos de GeolocationPositionError (1, 2, 3) a un motivo. */
export function problemFromCode(code: number): GeoProblem {
  switch (code) {
    case 1:
      return 'denied';
    case 3:
      return 'timeout';
    default:
      return 'unavailable';
  }
}

/** Qué hacer, dicho para el jugador. */
export function describeProblem(problem: GeoProblem): string {
  switch (problem) {
    case 'denied':
      return 'Bloqueaste el permiso de ubicación. Activalo en los ajustes del navegador para este sitio y volvé a intentar.';
    case 'timeout':
      return 'Tardó demasiado en encontrar tu ubicación. Probá de nuevo en un rato.';
    case 'unsupported':
      return 'Este navegador no puede darnos tu ubicación. Probá desde otro.';
    default:
      return 'No pudimos saber dónde estás. Revisá que la ubicación del celular esté activada y probá de nuevo.';
  }
}

/** La posición ya obtenida en esta pestaña, sin volver a pedirla. */
export function rememberedPosition(): Position | null {
  return remembered;
}

export function currentPosition(): Promise<Position> {
  if (remembered) {
    return Promise.resolve(remembered);
  }
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new GeoError('unsupported'));
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (position) => {
        remembered = { latitude: position.coords.latitude, longitude: position.coords.longitude };
        resolve(remembered);
      },
      (error) => reject(new GeoError(problemFromCode(error.code))),
      { enableHighAccuracy: false, timeout: TIMEOUT_MS, maximumAge: MAX_AGE_MS },
    );
  });
}

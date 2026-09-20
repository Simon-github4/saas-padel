/**
 * La ubicacion del celular, para que el servidor verifique que el socio esta en la sede
 * (asi nadie registra un ingreso desde su casa con una foto del QR).
 *
 * <p>Se pide con alta precision y un tope de espera: el GPS adentro de un edificio tarda, y una
 * pantalla que espera sin fin es peor que un mensaje. El servidor deja un margen de 200 m
 * justamente porque estas lecturas no son exactas.
 */

export interface Position {
  latitude: number;
  longitude: number;
}

/** Por que no se pudo obtener: cada motivo tiene un remedio distinto para el socio. */
export type GeoProblem = 'denied' | 'unavailable' | 'timeout' | 'unsupported';

export class GeoError extends Error {
  constructor(readonly problem: GeoProblem) {
    super(problem);
  }
}

const TIMEOUT_MS = 12_000;
/** Una lectura de hace menos de esto sirve: el socio no se movio del cartel. */
const MAX_AGE_MS = 30_000;

/** Los codigos de GeolocationPositionError (1, 2, 3) a un motivo. */
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

/** Que hacer, dicho para el socio. */
export function describeProblem(problem: GeoProblem): string {
  switch (problem) {
    case 'denied':
      return 'Bloqueaste el permiso de ubicación. Activalo en los ajustes del navegador para este sitio y volvé a intentar.';
    case 'timeout':
      return 'Tardó demasiado en encontrar tu ubicación. Probá de nuevo, cerca de una ventana o de la puerta.';
    case 'unsupported':
      return 'Este navegador no puede darnos tu ubicación. Probá desde otro.';
    default:
      return 'No pudimos saber dónde estás. Revisá que la ubicación del celular esté activada y probá de nuevo.';
  }
}

export function currentPosition(): Promise<Position> {
  return new Promise((resolve, reject) => {
    if (!navigator.geolocation) {
      reject(new GeoError('unsupported'));
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (position) => resolve({ latitude: position.coords.latitude, longitude: position.coords.longitude }),
      (error) => reject(new GeoError(problemFromCode(error.code))),
      { enableHighAccuracy: true, timeout: TIMEOUT_MS, maximumAge: MAX_AGE_MS },
    );
  });
}

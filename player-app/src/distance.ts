/** Radio medio de la Tierra, en km. */
const EARTH_RADIUS_KM = 6371;

export interface Coordinates {
  latitude: number;
  longitude: number;
}

/**
 * Distancia en línea recta entre dos puntos, en km (fórmula del haversine).
 *
 * <p>Es a vuelo de pájaro, no por calles: para ordenar clubes de una misma
 * ciudad alcanza, y no hace falta pedirle nada a ningún servicio de mapas.
 */
export function distanceKm(from: Coordinates, to: Coordinates): number {
  const dLat = toRadians(to.latitude - from.latitude);
  const dLng = toRadians(to.longitude - from.longitude);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRadians(from.latitude)) * Math.cos(toRadians(to.latitude)) * Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(a)));
}

/**
 * "a 850 m" o "a 2,3 km". Por debajo del km va en metros redondeados a 50: una
 * ubicación aproximada no da para más precisión, y "a 837 m" promete de más.
 */
export function formatDistance(km: number): string {
  if (km < 1) {
    const meters = Math.max(50, Math.round((km * 1000) / 50) * 50);
    return meters >= 1000 ? 'a 1 km' : `a ${meters} m`;
  }
  const rounded = km < 10 ? Math.round(km * 10) / 10 : Math.round(km);
  return `a ${rounded.toLocaleString('es-AR')} km`;
}

function toRadians(degrees: number): number {
  return (degrees * Math.PI) / 180;
}

package ar.com.padelnec.gym.service;

/**
 * Distancia entre dos puntos de la Tierra, para verificar que el socio esta en la
 * sede cuando registra el ingreso.
 */
public final class GymLocation {

    /** Radio medio de la Tierra, en metros. */
    private static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private GymLocation() {
    }

    /** Un punto que el celular informa: no hay forma de saber si miente, solo de pedirlo. */
    public record Point(double latitude, double longitude) {
    }

    /** Distancia en metros por el arco de circulo maximo (haversine); de sobra precisa para cientos de metros. */
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(a)));
    }
}

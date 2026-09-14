package ar.com.padelnec.web.dto;

import ar.com.padelnec.domain.enums.CourtRoof;
import ar.com.padelnec.domain.enums.CourtSurface;
import ar.com.padelnec.domain.enums.CourtWall;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Grilla del dia lista para pintar en la app del jugador.
 *
 * <p>Se organiza por horario y no por cancha porque asi decide el jugador: primero
 * a que hora puede jugar, y recien despues en cual de las canchas libres.
 */
public record AvailabilityResponse(
        ClubSummary club,
        LocalDate date,
        int slotDurationMinutes,
        List<CourtSummary> courts,
        List<SlotView> slots) {

    public record ClubSummary(
            String slug,
            String name,
            String timeZone,
            String whatsappNumber,
            /** Usuario de Instagram sin @, para mostrarlo; null si el club no cargo uno. */
            String instagramHandle,
            /** Link al perfil, ya armado: la app no tiene que saber como es la URL. */
            String instagramUrl,
            boolean allowUnpaidBooking,
            boolean acceptsOnlinePayments,
            BigDecimal depositPercentage,
            int cancellationLimitHours,
            int bookingHorizonDays,
            String tagline,
            String address,
            String city,
            String mapsUrl,
            String mapsEmbedQuery,
            BigDecimal latitude,
            BigDecimal longitude,
            String heroImageUrl,
            String heroHeadline,
            String heroCtaLabel,
            int heroOverlay,
            String heroVariant,
            int playersPerCourt,
            String themeMode,
            String primaryColor,
            String secondaryColor,
            List<AmenityView> amenities) {
    }

    public record AmenityView(String icon, String title, String description) {
    }

    public record CourtSummary(UUID id, String name) {
    }

    public record SlotView(
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime,
            Instant startsAt,
            Instant endsAt,
            List<CourtAvailability> available,
            boolean promo) {

        public boolean hasAvailability() {
            return !available.isEmpty();
        }

        /** Precio mas bajo del horario, para mostrar "desde $X" sin abrir el detalle. */
        public BigDecimal cheapestPrice() {
            return available.stream()
                    .map(CourtAvailability::price)
                    .min(BigDecimal::compareTo)
                    .orElse(null);
        }
    }

    /**
     * Una cancha libre en un horario. Lleva paredes, piso y techo para que el
     * jugador sepa en cual va a jugar antes de elegirla, y para que la busqueda filtre.
     */
    public record CourtAvailability(UUID courtId, String courtName, BigDecimal price,
                                    CourtWall wall, CourtSurface surface, CourtRoof roof) {
    }
}

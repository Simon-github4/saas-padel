package ar.com.padelnec.web.dto;

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
            boolean allowUnpaidBooking,
            boolean acceptsOnlinePayments,
            BigDecimal depositPercentage,
            int cancellationLimitHours) {
    }

    public record CourtSummary(UUID id, String name) {
    }

    public record SlotView(
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime,
            Instant startsAt,
            Instant endsAt,
            List<CourtAvailability> available) {

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

    public record CourtAvailability(UUID courtId, String courtName, BigDecimal price) {
    }
}

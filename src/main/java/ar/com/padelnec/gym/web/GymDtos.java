package ar.com.padelnec.gym.web;

import ar.com.padelnec.gym.service.GymAuthService.IssuedSession;
import ar.com.padelnec.gym.service.GymBillingService.Period;
import ar.com.padelnec.gym.service.GymCheckinService.CheckInResult;
import ar.com.padelnec.gym.service.GymStatusService.MembershipView;
import ar.com.padelnec.gym.service.GymStatusService.RecentCheckin;
import ar.com.padelnec.gym.service.GymStatusService.Status;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Contratos de la API de la app del gimnasio. */
public final class GymDtos {

    private GymDtos() {
    }

    public record LoginRequest(
            @NotBlank(message = "Necesitamos tu DNI") @Size(max = 20, message = "Ese DNI no es válido") String dni,
            // Solo se usa si el club pide clave: con solo el DNI no viaja.
            @Size(max = 200, message = "Esa clave es demasiado larga") String password) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Necesitamos tu clave actual") @Size(max = 200) String currentPassword,
            @NotBlank(message = "Necesitamos la clave nueva")
            @Size(min = 8, max = 200, message = "La clave nueva tiene que tener al menos 8 caracteres")
            String newPassword) {
    }

    /**
     * El socio no se manda en el cuerpo, a proposito: sale de la sesion. Un
     * {@code memberId} aca permitiria registrar un ingreso a nombre de otro.
     *
     * <p>La ubicacion del celular es opcional para el servidor: la exige solo la sede que tiene
     * coordenadas cargadas, y ahi responde con un mensaje que la app muestra tal cual.
     */
    public record CheckInRequest(
            @NotBlank(message = "Falta el código del QR") @Size(max = 200, message = "Ese código no es válido")
            String qrToken,
            @DecimalMin(value = "-90", message = "La ubicación no es válida")
            @DecimalMax(value = "90", message = "La ubicación no es válida") Double latitude,
            @DecimalMin(value = "-180", message = "La ubicación no es válida")
            @DecimalMax(value = "180", message = "La ubicación no es válida") Double longitude) {
    }

    public record ConfigResponse(
            String clubName,
            boolean passwordRequired,
            boolean locationRequired,
            String heroImageUrl,
            String themeMode,
            String primaryColor,
            String secondaryColor) {
    }

    public record SessionResponse(String token, Instant expiresAt, String fullName, boolean mustChangePassword) {

        static SessionResponse of(IssuedSession issued) {
            return new SessionResponse(issued.token(), issued.expiresAt(), issued.fullName(),
                    issued.mustChangePassword());
        }
    }

    /**
     * {@code used} y {@code limit}: los ingresos de la semana, del mes de la cuota o del pase
     * ({@code limitPeriod}: {@code WEEK}, {@code MONTH} o {@code PASS}), que termina el {@code windowEnd}.
     */
    public record CheckInResponse(String sedeName, boolean alreadyRegistered, int used, int limit,
                                  String limitPeriod, LocalDate windowEnd, LocalDate validUntil) {

        static CheckInResponse of(CheckInResult result) {
            return new CheckInResponse(result.sedeName(), result.alreadyRegistered(), result.used(),
                    result.limit(), result.window().name(), result.windowEnd(), result.validUntil());
        }
    }

    /** {@code planLength}: {@code MONTH} o {@code FIFTEEN_DAYS} (pase de 15 dias). */
    public record MembershipResponse(LocalDate startsOn, LocalDate endsOn, int daysPerWeek, List<String> sedes,
                                     String planLength) {

        static MembershipResponse of(MembershipView view) {
            return view == null ? null
                    : new MembershipResponse(view.startsOn(), view.endsOn(), view.daysPerWeek(), view.sedes(),
                            view.planLength().name());
        }
    }

    public record RecentCheckinResponse(LocalDate date, String sedeName) {

        static RecentCheckinResponse of(RecentCheckin checkin) {
            return new RecentCheckinResponse(checkin.date(), checkin.sedeName());
        }
    }

    /** {@code used}, {@code limit}, {@code limitPeriod} y {@code windowEnd}: como en {@link CheckInResponse}. */
    public record MeResponse(String fullName, MembershipResponse membership, boolean valid, int used,
                             int limit, String limitPeriod, LocalDate windowEnd, boolean checkedInToday,
                             List<RecentCheckinResponse> recent,
                             LocalDate cycleStart, LocalDate periodStart, LocalDate periodEnd,
                             LocalDate paidUntil, boolean paidCurrent, int monthsLate, boolean canEnter,
                             java.math.BigDecimal owedTotal, boolean onPasses, Period gap, Period paidAhead) {

        static MeResponse of(Status status) {
            return new MeResponse(status.fullName(), MembershipResponse.of(status.membership()), status.valid(),
                    status.used(), status.limit(), status.window().name(), status.windowEnd(),
                    status.checkedInToday(),
                    status.recent().stream().map(RecentCheckinResponse::of).toList(),
                    status.cycleStart(), status.periodStart(), status.periodEnd(), status.paidUntil(),
                    status.paidCurrent(), status.monthsLate(), status.canEnter(), status.owedTotal(),
                    status.onPasses(), status.gap(), status.paidAhead());
        }
    }
}

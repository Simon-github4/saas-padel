package ar.com.padelnec.domain;

import ar.com.padelnec.domain.enums.BlackoutKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** Franja en la que el club no toma reservas: mantenimiento, torneo, feriado, clase. */
@Entity
@Table(name = "blackout")
@Getter
@Setter
public class Blackout extends TenantScopedEntity {

    /** Nulo = afecta a todas las canchas del club. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "court_id")
    private Court court;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    /** En un torneo, {@code reason} es el nombre del torneo. */
    @Column(length = 160)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BlackoutKind kind = BlackoutKind.SUSPENSION;

    public boolean isTournament() {
        return kind == BlackoutKind.TOURNAMENT;
    }

    public boolean appliesTo(Court candidate) {
        return court == null || court.getId().equals(candidate.getId());
    }

    public boolean overlaps(Instant slotStart, Instant slotEnd) {
        return startTime.isBefore(slotEnd) && endTime.isAfter(slotStart);
    }
}

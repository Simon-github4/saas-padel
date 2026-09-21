package ar.com.padelnec.gym.repository;

import ar.com.padelnec.gym.domain.GymMembership;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GymMembershipRepository extends JpaRepository<GymMembership, UUID> {

    /** El periodo del socio que vale ese dia (no anulado). */
    @Query("""
            select m from GymMembership m
            where m.member.id = :memberId and m.voidedAt is null
              and :day between m.startsOn and m.endsOn
            """)
    Optional<GymMembership> findCurrent(@Param("memberId") UUID memberId, @Param("day") LocalDate day);

    /** El ultimo periodo del socio, aunque ya haya vencido. */
    Optional<GymMembership> findFirstByMemberIdAndVoidedAtIsNullOrderByEndsOnDesc(UUID memberId);

    /** Los periodos que valen ese dia, de todos los socios. */
    @Query("""
            select m from GymMembership m join fetch m.member
            where m.voidedAt is null and :day between m.startsOn and m.endsOn
            """)
    List<GymMembership> findAllCurrent(@Param("day") LocalDate day);

    /** El ultimo periodo de cada socio; los periodos de un socio no se pisan, asi que es uno. */
    @Query("""
            select m from GymMembership m join fetch m.member
            where m.voidedAt is null
              and m.endsOn = (select max(x.endsOn) from GymMembership x
                              where x.member = m.member and x.voidedAt is null)
            """)
    List<GymMembership> findLatestPerMember();

    /** Cuantos periodos no anulados del socio se pisan con ese rango. */
    @Query("""
            select count(m) from GymMembership m
            where m.member.id = :memberId and m.voidedAt is null
              and m.startsOn <= :to and m.endsOn >= :from
            """)
    long countOverlapping(@Param("memberId") UUID memberId,
                          @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Los cobros cargados en una ventana de tiempo, para el resumen del dia. */
    @Query("""
            select m from GymMembership m join fetch m.member join fetch m.collectedSede
            where m.voidedAt is null and m.createdAt >= :from and m.createdAt < :to
            order by m.createdAt desc
            """)
    List<GymMembership> findCreatedBetween(@Param("from") Instant from, @Param("to") Instant to);

    /** Todas las cuotas pagas del socio, para calcular su deuda y su plan vigente. */
    List<GymMembership> findAllByMemberIdAndVoidedAtIsNullOrderByEndsOnDesc(UUID memberId);
}

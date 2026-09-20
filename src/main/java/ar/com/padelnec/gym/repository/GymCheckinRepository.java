package ar.com.padelnec.gym.repository;

import ar.com.padelnec.gym.domain.GymCheckin;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface GymCheckinRepository extends JpaRepository<GymCheckin, UUID> {

    /**
     * Valor de {@code distanceMeters} cuando no se verifico la ubicacion. Un centinela y no
     * un null porque un parametro nulo en SQL nativo no lleva tipo y Postgres lo rechaza; el
     * SQL lo convierte a NULL con NULLIF.
     */
    int NO_DISTANCE = -1;

    /** Cuantos dias de la semana uso el socio (los dias se cuentan por fecha local del club). */
    long countByMemberIdAndLocalDateBetween(UUID memberId, LocalDate from, LocalDate to);

    @EntityGraph(attributePaths = "sede")
    Optional<GymCheckin> findByMemberIdAndLocalDate(UUID memberId, LocalDate localDate);

    @EntityGraph(attributePaths = "sede")
    List<GymCheckin> findTop10ByMemberIdOrderByLocalDateDesc(UUID memberId);

    @Query("""
            select c from GymCheckin c join fetch c.member join fetch c.sede
            where c.localDate = :day
            order by c.checkedInAt desc
            """)
    List<GymCheckin> findAllByDay(@Param("day") LocalDate day);

    /** Dias usados por cada socio en un rango, para la grilla del panel. */
    @Query("""
            select c.member.id as memberId, count(c) as total from GymCheckin c
            where c.localDate between :from and :to
            group by c.member.id
            """)
    List<MemberCount> countByMemberBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    interface MemberCount {
        UUID getMemberId();

        long getTotal();
    }

    /**
     * Inserta el ingreso del socio si todavia no tiene uno ese dia; devuelve 1 si lo
     * inserto y 0 si ya existia. {@code ON CONFLICT DO NOTHING} y no un {@code save}
     * con {@code catch}: la excepcion de la constraint deja la transaccion marcada
     * para rollback aunque se la atrape, y dos escaneos seguidos son lo normal.
     *
     * <p>Es SQL nativo, asi que no lo filtra Hibernate: el {@code club_id} lo pasa
     * el servicio desde el club en contexto, y el socio y la sede que llegan ya se
     * cargaron con el filtro puesto.
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO gym_checkin (id, club_id, member_id, membership_id, sede_id,
                                     checked_in_at, local_date, distance_m, is_override,
                                     registered_by, created_at, updated_at)
            VALUES (gen_random_uuid(), :clubId, :memberId, :membershipId, :sedeId,
                    :checkedInAt, :localDate, NULLIF(:distanceMeters, -1), :override, NULL,
                    now(), now())
            ON CONFLICT (member_id, local_date) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("clubId") UUID clubId, @Param("memberId") UUID memberId,
                       @Param("membershipId") UUID membershipId, @Param("sedeId") UUID sedeId,
                       @Param("checkedInAt") Instant checkedInAt, @Param("localDate") LocalDate localDate,
                       @Param("distanceMeters") int distanceMeters, @Param("override") boolean override);

    /** Igual que {@link #insertIfAbsent}, pero cargado a mano por un usuario del panel. */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO gym_checkin (id, club_id, member_id, membership_id, sede_id,
                                     checked_in_at, local_date, distance_m, is_override,
                                     registered_by, created_at, updated_at)
            VALUES (gen_random_uuid(), :clubId, :memberId, :membershipId, :sedeId,
                    :checkedInAt, :localDate, NULLIF(:distanceMeters, -1), :override, :registeredBy,
                    now(), now())
            ON CONFLICT (member_id, local_date) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsentByStaff(@Param("clubId") UUID clubId, @Param("memberId") UUID memberId,
                              @Param("membershipId") UUID membershipId, @Param("sedeId") UUID sedeId,
                              @Param("checkedInAt") Instant checkedInAt, @Param("localDate") LocalDate localDate,
                              @Param("distanceMeters") int distanceMeters, @Param("override") boolean override,
                              @Param("registeredBy") UUID registeredBy);
}

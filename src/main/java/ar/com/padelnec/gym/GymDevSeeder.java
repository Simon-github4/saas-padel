package ar.com.padelnec.gym;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.gym.domain.GymClubConfig;
import ar.com.padelnec.gym.domain.GymMember;
import ar.com.padelnec.gym.domain.GymSede;
import ar.com.padelnec.gym.domain.PayMethod;
import ar.com.padelnec.gym.repository.GymCheckinRepository;
import ar.com.padelnec.gym.repository.GymClubConfigRepository;
import ar.com.padelnec.gym.repository.GymMemberRepository;
import ar.com.padelnec.gym.repository.GymMembershipRepository;
import ar.com.padelnec.gym.service.GymMemberService;
import ar.com.padelnec.gym.service.GymMembershipService;
import ar.com.padelnec.gym.service.GymMembershipService.Sale;
import ar.com.padelnec.gym.service.GymOverviewService;
import ar.com.padelnec.gym.service.GymSedeService;
import ar.com.padelnec.gym.service.GymTariffService;
import ar.com.padelnec.gym.service.GymWeek;
import ar.com.padelnec.service.TenantService;
import ar.com.padelnec.web.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Prende el gimnasio en el club de ejemplo de desarrollo y le carga dos sedes y tres
 * socios, para poder probar la app y el panel apenas arranca. Aparte, suma una tanda de
 * socios con los casos del mostrador (sin DNI, repetidos, con deuda, adelantados, con
 * una cuota anulada...) para probar la carga y las correcciones.
 *
 * <p>Solo bajo el perfil {@code dev}, y dentro de este paquete (no en el
 * {@code DevDataSeeder} de padel) para que el modulo siga sin tocar nada de afuera.
 * Es idempotente: si el club ya tiene el modulo prendido, no toca nada.
 *
 * <p>El club entra solo con el DNI (lo normal). Los tres socios tienen ademas la clave
 * {@value #DEV_PASSWORD}, que solo cuenta si se prende el modo "DNI y clave": temporal como
 * la de un alta real, la primera vez la app pide cambiarla.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
class GymDevSeeder {

    /** El club de la documentacion, el mismo que siembra DevDataSeeder. */
    private static final String CLUB_SLUG = "club-necochea";
    static final String DEV_PASSWORD = "gimnasio1234";

    private final TenantService tenantService;
    private final GymModule gymModule;
    private final GymClubConfigRepository configRepository;
    private final GymSedeService sedeService;
    private final GymMemberRepository memberRepository;
    private final GymMembershipService membershipService;
    private final GymOverviewService overviewService;
    private final GymMemberService memberService;
    private final GymTariffService tariffService;
    private final GymMembershipRepository membershipRepository;
    private final GymCheckinRepository checkinRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Cuando la aplicacion ya termino de arrancar, no como un {@code ApplicationRunner}: el club de
     * ejemplo lo crea el {@code DevDataSeeder} de padel, que es otro runner, y el orden entre dos
     * runners sin {@code @Order} no esta garantizado. Este evento llega despues de todos.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void seedWhenReady() {
        Tenant club;
        try {
            club = tenantService.activate(CLUB_SLUG);
        } catch (ResourceNotFoundException ex) {
            log.info("Gimnasio de ejemplo: no esta el club {}, no se siembra nada", CLUB_SLUG);
            return;
        }
        try {
            TenantContext.runAs(club.getId(), this::seed);
        } finally {
            TenantContext.clear();
        }
    }

    private void seed() {
        if (!gymModule.isEnabled()) {
            seedBase();
        }
        // Aparte del alta del modulo, para que tambien le lleguen a una base de desarrollo que ya
        // tenia el gimnasio prendido. Hernan Paz hace de marca: si esta, ya se sembro.
        if (memberRepository.findByDni(CASES_MARKER_DNI).isEmpty()) {
            seedCases();
        }
    }

    private void seedBase() {
        configRepository.save(new GymClubConfig());

        GymSede necochea = sedeService.create("Necochea", "Calle 62 Nº 2500", BigDecimal.ZERO);
        GymSede quequen = sedeService.create("Quequén", "Av. Costanera 800", new BigDecimal("50"));
        LocalDate today = overviewService.today();

        GymMember ana = member("30111222", "Ana Gómez");
        GymMember beto = member("40222333", "Beto Ruiz");
        member("50333444", "Carla Díaz");

        // Ana: cuota vigente, 3 dias por semana, en las dos sedes.
        sell(ana, today.minusDays(5), today.plusDays(24), 3, necochea, quequen);
        // Beto: la cuota le vencio hace una semana.
        sell(beto, today.minusDays(37), today.minusDays(7), 2, necochea);
        // Carla no tiene ninguna cuota.

        log.info("Gimnasio de ejemplo listo en {}: socios 30111222 (vigente), 40222333 (vencida) y "
                + "50333444 (sin cuota); entran con el DNI (con clave, seria {})", CLUB_SLUG, DEV_PASSWORD);
    }

    /** El DNI de Hernan Paz, el socio que marca que los casos ya se sembraron. */
    private static final String CASES_MARKER_DNI = "33444555";

    /**
     * Socios para probar el mostrador, relativos a hoy. Todos en la primera sede activa,
     * con la tarifa de sus dias por semana (se fija si falta).
     */
    private void seedCases() {
        List<GymSede> sedes = sedeService.active();
        if (sedes.isEmpty()) {
            return;
        }
        GymSede sede = sedes.getFirst();
        LocalDate today = overviewService.today();
        int[] prices = {18000, 25000, 30000, 34000, 38000, 42000, 45000};
        for (int days = 1; days <= 7; days++) {
            if (tariffService.priceOf(days) == null) {
                tariffService.setPrice(days, new BigDecimal(prices[days - 1]));
            }
        }

        // Sin DNI: uno con cuota vigente (para "Cargar DNI y datos") y otra sin cuota.
        UUID diego = memberService.create(null, "Diego Fernández", "2262 401122").id();
        charge(diego, 1, 2, sede, today, today.minusDays(10), null, PayMethod.CASH);
        memberService.create(null, "Elena Sosa", null);

        // El caso de Los Troncos: el mismo socio cargado dos veces sin DNI. El primero quedo con 3
        // dias por error y, al "cambiarlo" cobrando otra cuota de 2, se le fue al mes siguiente
        // (para Corregir / Anular). El segundo es el que quedo bien. Ninguno vino: se pueden eliminar.
        UUID agustin = memberService.create(null, "Agustín Coupau", null).id();
        charge(agustin, 1, 3, sede, today, today.minusDays(2), null, PayMethod.CASH);
        charge(agustin, 1, 2, sede, today, null, null, PayMethod.CASH);
        UUID agustinBien = memberService.create(null, "Agustín Coupau", null).id();
        charge(agustinBien, 1, 2, sede, today, today.minusDays(2), null, PayMethod.CASH);

        // Pago solo la primera cuota, hace tres meses: adeuda y no puede entrar
        // (para "Nuevo inicio de mes", el que vuelve despues de meses).
        UUID facundo = memberService.create("20111222", "Facundo Molina", null).id();
        charge(facundo, 1, 3, sede, today, today.minusMonths(3).minusDays(4), today.minusMonths(3).minusDays(4),
                PayMethod.CASH);

        // Pago el mes corriente y dos adelantados, por transferencia.
        UUID gabriela = memberService.create("21222333", "Gabriela Torres", null).id();
        charge(gabriela, 3, 4, sede, today, today.minusDays(5), today.minusDays(5), PayMethod.TRANSFER);

        // Vino tres veces (no se puede eliminar) y tiene un adelanto anulado (se ve tachado).
        UUID hernan = memberService.create(CASES_MARKER_DNI, "Hernán Paz", "2262 505050").id();
        charge(hernan, 2, 3, sede, today, today.minusDays(12), today.minusDays(12), PayMethod.CASH);
        UUID current = membershipRepository.findAllByMemberIdAndVoidedAtIsNullOrderByEndsOnDesc(hernan)
                .getLast().getId();
        membershipService.voidMembership(membershipRepository
                .findFirstByMemberIdAndVoidedAtIsNullOrderByEndsOnDesc(hernan).orElseThrow().getId());
        for (int daysAgo : new int[] {10, 8, 3}) {
            checkIn(hernan, current, sede, today.minusDays(daysAgo));
        }

        // Cuota vigente pero deshabilitada.
        UUID ines = memberService.create("34555666", "Inés Ramos", null).id();
        charge(ines, 1, 3, sede, today, today.minusDays(7), today.minusDays(7), PayMethod.CASH);
        memberService.setEnabled(ines, false);

        // Pago ayer y se cargo hoy: va a la caja de ayer ("cargado dd/MM").
        UUID julian = memberService.create("35666777", "Julián Vega", null).id();
        charge(julian, 1, 3, sede, today, today.minusDays(1), today.minusDays(1), PayMethod.TRANSFER);

        // Su cuota empieza en unos dias.
        UUID lucia = memberService.create("36777888", "Lucía Medina", null).id();
        charge(lucia, 1, 5, sede, today, today.plusDays(5), null, PayMethod.CASH);

        // 2 dias por semana y ya vino los dos de esta semana (tope), cuando se puede.
        UUID martin = memberService.create("37888999", "Martín Álvarez", null).id();
        charge(martin, 1, 2, sede, today, today.minusDays(15), today.minusDays(15), PayMethod.CASH);
        UUID martinFee = membershipRepository.findAllByMemberIdAndVoidedAtIsNullOrderByEndsOnDesc(martin)
                .getFirst().getId();
        GymWeek week = GymWeek.of(today);
        for (LocalDate day = week.monday(); day.isBefore(today) && day.isBefore(week.monday().plusDays(2));
                day = day.plusDays(1)) {
            checkIn(martin, martinFee, sede, day);
        }

        log.info("Gimnasio de ejemplo: casos del mostrador sembrados en {} (sin DNI, repetidos, deuda, "
                + "adelantos, anulada, deshabilitado, cobro de ayer, cuota futura, tope semanal)", CLUB_SLUG);
    }

    private void charge(UUID memberId, int months, int daysPerWeek, GymSede sede, LocalDate today,
                        LocalDate newStart, LocalDate paidOn, PayMethod method) {
        membershipService.charge(memberId, months, null, daysPerWeek, method, sede.getId(), Set.of(sede.getId()),
                null, today, newStart, paidOn);
    }

    /** Un ingreso de un dia pasado, a las 19 del club. */
    private void checkIn(UUID memberId, UUID membershipId, GymSede sede, LocalDate day) {
        ZoneId zone = overviewService.zone();
        checkinRepository.insertIfAbsent(TenantContext.require(), memberId, membershipId, sede.getId(),
                day.atTime(19, 0).atZone(zone).toInstant(), day, GymCheckinRepository.NO_DISTANCE, false);
    }

    private GymMember member(String dni, String name) {
        GymMember member = new GymMember();
        member.setDni(dni);
        member.setFullName(name);
        member.setPasswordHash(passwordEncoder.encode(DEV_PASSWORD));
        member.setMustChangePassword(true);
        return memberRepository.save(member);
    }

    private void sell(GymMember member, LocalDate from, LocalDate to, int daysPerWeek, GymSede... sedes) {
        Set<java.util.UUID> sedeIds = new java.util.HashSet<>();
        for (GymSede sede : sedes) {
            sedeIds.add(sede.getId());
        }
        membershipService.sell(new Sale(member.getId(), from, to, daysPerWeek, new BigDecimal("30000"),
                PayMethod.CASH, sedes[0].getId(), sedeIds, null, null));
    }
}

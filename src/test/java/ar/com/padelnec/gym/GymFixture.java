package ar.com.padelnec.gym;

import ar.com.padelnec.config.TenantContext;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.gym.domain.GymClubConfig;
import ar.com.padelnec.gym.domain.GymSede;
import ar.com.padelnec.gym.domain.PayMethod;
import ar.com.padelnec.gym.repository.GymClubConfigRepository;
import ar.com.padelnec.gym.service.GymMemberService;
import ar.com.padelnec.gym.service.GymMemberService.CreatedMember;
import ar.com.padelnec.gym.service.GymMembershipService;
import ar.com.padelnec.gym.service.GymMembershipService.Sale;
import ar.com.padelnec.gym.service.GymSedeService;
import ar.com.padelnec.gym.service.GymSedeService.SedeData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.context.TestComponent;

/**
 * Datos de gimnasio para los tests. No limpia nada: las tablas gym_* cuelgan del
 * club con ON DELETE CASCADE, asi que {@code ClubFixture.reset()} las arrastra al
 * borrar los clubes.
 *
 * <p>Cada metodo corre con el club indicado en contexto, como lo hace la API.
 */
@TestComponent
@RequiredArgsConstructor
public class GymFixture {

    private final GymClubConfigRepository configRepository;
    private final GymSedeService sedeService;
    private final GymMemberService memberService;
    private final GymMembershipService membershipService;

    /** Prende el modulo para el club, como lo haria el INSERT documentado: entran solo con el DNI. */
    public void enable(Tenant club) {
        enable(club, false);
    }

    /** Prende el modulo eligiendo si los socios entran con clave ademas del DNI. */
    public void enable(Tenant club, boolean passwordRequired) {
        TenantContext.runAs(club.getId(), () -> {
            GymClubConfig config = new GymClubConfig();
            config.setPasswordRequired(passwordRequired);
            configRepository.save(config);
        });
    }

    public GymSede sede(Tenant club, String name) {
        return TenantContext.callAs(club.getId(), () -> sedeService.create(name, null, BigDecimal.ZERO));
    }

    /** Una sede con ubicacion cargada: el socio tiene que estar a menos de {@code radiusMeters}. */
    public GymSede sedeAt(Tenant club, String name, double latitude, double longitude, int radiusMeters) {
        return TenantContext.callAs(club.getId(), () -> sedeService.create(
                new SedeData(name, null, BigDecimal.ZERO, latitude, longitude, radiusMeters)));
    }

    public CreatedMember member(Tenant club, String dni, String name) {
        return TenantContext.callAs(club.getId(), () -> memberService.create(dni, name, null));
    }

    /** Cobra una cuota del socio, valida en las sedes indicadas y cobrada en la primera. */
    public UUID sell(Tenant club, UUID memberId, LocalDate from, LocalDate to, int daysPerWeek,
                     GymSede... sedes) {
        return TenantContext.callAs(club.getId(), () -> membershipService.sell(new Sale(memberId, from, to,
                daysPerWeek, new BigDecimal("30000"), PayMethod.CASH, sedes[0].getId(),
                Arrays.stream(sedes).map(GymSede::getId).collect(Collectors.toSet()), null)));
    }
}

package ar.com.padelnec.service;

import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.Customer;
import ar.com.padelnec.domain.PlayerAccount;
import ar.com.padelnec.payment.DepositPayer;
import ar.com.padelnec.repository.PlayerAccountRepository;
import ar.com.padelnec.support.PhoneNumbers;
import ar.com.padelnec.support.PhoneNumbers.AreaCodeAndNumber;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Junta lo que se sabe de quien reserva para mandarselo a MercadoPago junto con la
 * sena. Ver {@link DepositPayer} por que importa.
 */
@Component
@RequiredArgsConstructor
public class DepositPayerResolver {

    private final PlayerAccountRepository playerAccountRepository;
    private final PhoneNumbers phoneNumbers;

    public DepositPayer resolve(Booking booking) {
        Customer customer = booking.getCustomer();
        // El nombre que se escribio en esta reserva y no el del jugador: es quien
        // esta por pagar, aunque haya puesto el telefono de otro.
        String name = booking.getBookedName() != null ? booking.getBookedName() : customer.getFullName();
        String[] names = splitName(name);

        Optional<AreaCodeAndNumber> phone = phoneNumbers.splitAreaCode(customer.getPhoneNumber());
        Optional<PlayerAccount> account = Optional.ofNullable(booking.getPlayerAccountId())
                .flatMap(playerAccountRepository::findById);

        return new DepositPayer(
                names[0],
                names[1],
                account.filter(PlayerAccount::isEmailVerified).map(PlayerAccount::getEmail).orElse(null),
                phone.map(AreaCodeAndNumber::areaCode).orElse(null),
                phone.map(AreaCodeAndNumber::number).orElse(null));
    }

    /**
     * Nombre y apellido a partir de lo que escribio el jugador en un solo campo.
     *
     * <p>La primera palabra es el nombre y el resto el apellido. Falla con los
     * nombres compuestos ("Juan Pablo Perez" queda Juan / Pablo Perez), pero para el
     * antifraude alcanza con que el nombre completo este, sin importar el corte.
     */
    static String[] splitName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return new String[] {null, null};
        }
        String[] parts = fullName.trim().split("\\s+", 2);
        return new String[] {parts[0], parts.length > 1 ? parts[1] : null};
    }
}

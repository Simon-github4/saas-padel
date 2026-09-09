package ar.com.padelnec.service;

import ar.com.padelnec.domain.Booking;
import ar.com.padelnec.domain.ClubUser;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.PaymentMethod;
import ar.com.padelnec.domain.enums.PaymentStatus;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.repository.CashMovementRow;
import ar.com.padelnec.repository.ClubUserRepository;
import ar.com.padelnec.repository.KioskSaleRow;
import ar.com.padelnec.repository.PaymentRepository;
import ar.com.padelnec.repository.ProductSaleRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La caja de un dia: que plata se movio, por que via y quien la cargo.
 *
 * <p>Es una lectura, no un libro de caja: todavia no hay apertura, ni fondo
 * inicial, ni gastos, ni cierre firmado. Lo que responde es la pregunta que el
 * mostrador se hace antes de irse, cuanto tiene que haber en el cajon, con lo
 * que el sistema ya sabe.
 *
 * <p>Las estadisticas ({@link BookingStatsService}) miran lo mismo desde otro
 * lado: alla el rango es largo y el foco esta en el turno; aca es un dia y el
 * foco esta en el movimiento.
 */
@Service
@RequiredArgsConstructor
public class CashRegisterService {

    /**
     * El orden en que se muestran los metodos: fijo, no por importe. El mostrador
     * busca siempre el efectivo en el mismo lugar, y unas tarjetas que se
     * reordenan solas segun el dia lo obligan a leerlas de nuevo cada vez.
     * Efectivo primero porque es el unico que se cuenta a mano.
     */
    private static final List<PaymentMethod> METHOD_ORDER =
            List.of(PaymentMethod.CASH, PaymentMethod.TRANSFER, PaymentMethod.MERCADOPAGO);

    private final PaymentRepository paymentRepository;
    private final ProductSaleRepository productSaleRepository;
    private final BookingRepository bookingRepository;
    private final ClubUserRepository clubUserRepository;
    private final SlotGenerator slotGenerator;

    /**
     * Un movimiento de plata del dia.
     *
     * <p>{@code amount} negativo es una devolucion. {@code registeredByName} es
     * nulo cuando no lo cargo una persona: los pagos de MercadoPago los asienta
     * el webhook.
     */
    public record Movement(Instant at, PaymentMethod method, BigDecimal amount,
                           String registeredByName, String customerName, String courtName,
                           Instant bookingStartTime) {

        public boolean isRefund() {
            return amount.signum() < 0;
        }
    }

    public record MethodTotal(PaymentMethod method, BigDecimal total) {
    }

    public record KioskLine(String productName, int quantity, BigDecimal total) {
    }

    /**
     * La caja de un dia entera, en una sola lectura.
     *
     * <p>{@code cash} sale aparte del resto de {@code byMethod} porque es el unico
     * numero que se contrasta contra billetes: la transferencia y MercadoPago hay
     * que buscarlos en un banco, no en el cajon.
     */
    public record DayCash(LocalDate date, List<Movement> movements, List<MethodTotal> byMethod,
                          BigDecimal total, BigDecimal cash, BigDecimal pending, int pendingBookings,
                          List<KioskLine> kiosk, BigDecimal kioskTotal) {
    }

    @Transactional(readOnly = true)
    public DayCash of(Tenant club, LocalDate date) {
        // El dia de caja es el dia calendario del club, no su dia operativo: un
        // club que cierra a las 2 AM cobra despues de medianoche, y ese cobro es
        // del dia nuevo mientras no exista un cierre de caja de verdad que diga
        // otra cosa. Los turnos pendientes de mas abajo si van por el dia
        // operativo, que es el que el mostrador tiene delante.
        Instant from = date.atStartOfDay(club.zoneId()).toInstant();
        Instant until = date.plusDays(1).atStartOfDay(club.zoneId()).toInstant();

        Map<UUID, String> names = staffNames(club);
        List<Movement> movements = paymentRepository
                .findMovementsBetween(from, until, PaymentStatus.APPROVED).stream()
                .map(row -> toMovement(row, names))
                .toList();

        List<MethodTotal> byMethod = totalsByMethod(movements);
        BigDecimal total = byMethod.stream().map(MethodTotal::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cash = byMethod.stream()
                .filter(entry -> entry.method() == PaymentMethod.CASH)
                .map(MethodTotal::total)
                .findFirst()
                .orElse(BigDecimal.ZERO);

        List<KioskLine> kiosk = kioskLines(from, until);
        BigDecimal kioskTotal = kiosk.stream().map(KioskLine::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Booking> pendientes = bookingRepository.findAgenda(
                        slotGenerator.dayStart(club, date), slotGenerator.dayEnd(club, date)).stream()
                .filter(booking -> booking.getStatus().occupiesSlot())
                .filter(booking -> booking.balanceDue().signum() > 0)
                .toList();
        BigDecimal pending = pendientes.stream().map(Booking::balanceDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new DayCash(date, movements, byMethod, total, cash, pending, pendientes.size(),
                kiosk, kioskTotal);
    }

    // ------------------------------------------------------------ internos

    private Movement toMovement(CashMovementRow row, Map<UUID, String> names) {
        return new Movement(row.createdAt(), row.method(), row.amount(),
                row.registeredBy() == null ? null : names.get(row.registeredBy()),
                row.customerFullName(), row.courtName(), row.bookingStartTime());
    }

    /**
     * Los tres metodos siempre, incluso en cero.
     *
     * <p>Un dia sin efectivo tiene que decir cero, no esconder la tarjeta: que
     * falte se lee como que el dato no se calculo, y es justo el numero que el
     * mostrador va a buscar para cerrar.
     */
    private List<MethodTotal> totalsByMethod(List<Movement> movements) {
        Map<PaymentMethod, BigDecimal> sums = movements.stream()
                .collect(Collectors.groupingBy(Movement::method,
                        Collectors.reducing(BigDecimal.ZERO, Movement::amount, BigDecimal::add)));
        return METHOD_ORDER.stream()
                .map(method -> new MethodTotal(method, sums.getOrDefault(method, BigDecimal.ZERO)))
                .toList();
    }

    /** Las lineas de kiosco del dia, agrupadas por producto y de mayor a menor. */
    private List<KioskLine> kioskLines(Instant from, Instant until) {
        Map<String, List<KioskSaleRow>> byProduct = productSaleRepository.findSalesBetween(from, until)
                .stream()
                .collect(Collectors.groupingBy(KioskSaleRow::productName));

        return byProduct.entrySet().stream()
                .map(entry -> new KioskLine(entry.getKey(),
                        entry.getValue().stream().mapToInt(KioskSaleRow::quantity).sum(),
                        entry.getValue().stream().map(KioskSaleRow::subtotal)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)))
                .sorted(Comparator.comparing(KioskLine::total).reversed())
                .toList();
    }

    /**
     * Nombre de cada usuario del club, para poder mostrar quien cargo un cobro.
     *
     * <p>{@code registered_by} se venia guardando desde el principio en
     * {@code payment} y {@code product_sale}, y hasta ahora no lo leia nadie.
     */
    private Map<UUID, String> staffNames(Tenant club) {
        return clubUserRepository.findAllByClubIdOrderByFullNameAsc(club.getId()).stream()
                .collect(Collectors.toMap(ClubUser::getId, ClubUser::getFullName,
                        (first, second) -> first, LinkedHashMap::new));
    }
}

package ar.com.padelnec.service;

import ar.com.padelnec.domain.Blackout;
import ar.com.padelnec.domain.Court;
import ar.com.padelnec.domain.PlayerAccount;
import ar.com.padelnec.domain.Tenant;
import ar.com.padelnec.domain.enums.BookingStatus;
import ar.com.padelnec.domain.enums.CancellationReason;
import ar.com.padelnec.domain.enums.PaymentMethod;
import ar.com.padelnec.domain.enums.PaymentStatus;
import ar.com.padelnec.repository.BlackoutRepository;
import ar.com.padelnec.repository.BookingRepository;
import ar.com.padelnec.repository.BookingStatsRow;
import ar.com.padelnec.repository.CourtRepository;
import ar.com.padelnec.repository.PaymentCashRow;
import ar.com.padelnec.repository.PaymentRepository;
import ar.com.padelnec.repository.PlayerAccountRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agrega turnos en estadisticas del club: ingresos, ocupacion, horarios pico y
 * top clientes.
 *
 * <p>La agregacion ocurre en memoria sobre {@link BookingRepository#findStatsBetween},
 * no en SQL: una consulta nativa se saltearia el filtro por club de Hibernate, y el
 * volumen de un club en un año entra sin problema en memoria.
 */
@Service
@RequiredArgsConstructor
public class BookingStatsService {

    private static final Locale ES_AR = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("d/MM", ES_AR);
    private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy", ES_AR);
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("EEEE d/MM", ES_AR);

    private final BookingRepository bookingRepository;
    private final CourtRepository courtRepository;
    private final PaymentRepository paymentRepository;
    private final BlackoutRepository blackoutRepository;
    private final PlayerAccountRepository playerAccountRepository;
    private final SlotGenerator slotGenerator;

    public enum Periodo {
        DIA, SEMANA, MES, ANIO
    }

    /**
     * Una fila de la tabla de periodos: metricas de un bucket (semana, mes o año).
     *
     * <p>Ojo con las dos columnas de plata, porque no se cuentan igual y no tienen
     * por que dar lo mismo. {@code facturado} es lo que valen los turnos que caen
     * en el periodo: responde "cuanto vendio la cancha". {@code cobrado} es la
     * plata que entro dentro del periodo, sin importar para cuando era el turno ni
     * si despues se cancelo: responde "cuanto paso por la caja". Una sena cobrada
     * hoy por un turno del mes que viene suma al cobrado de hoy y al facturado del
     * mes que viene.
     */
    public record PeriodStats(String label, LocalDate from, LocalDate to, int jugados,
                              Duration horasJugadas, int reservados, BigDecimal facturado,
                              BigDecimal cobrado, int cancelados, int noShows,
                              int slotsOcupados, int slotsPosibles, BigDecimal ocupacionPromedio) {
    }

    public record HourlyStat(java.time.LocalTime hour, int turnos, BigDecimal facturado) {
    }

    public record CustomerStat(String name, String phone, int turnos, BigDecimal facturado) {
    }

    public record CancellationStat(CancellationReason reason, int count) {
    }

    public record PaymentMethodStat(PaymentMethod method, BigDecimal total) {
    }

    /**
     * Una fila por bucket del periodo elegido, entre from y to (inclusive).
     *
     * <p>El primer y el ultimo bucket casi nunca coinciden justo con "desde"/"hasta"
     * (una semana o un mes no empiezan ni terminan ahi): se acotan al rango elegido
     * para que la suma de las filas de la tabla coincida con {@link #summary}, en vez
     * de que el ultimo mes cuente dias posteriores a "hasta" que las tarjetas no ven.
     */
    @Transactional(readOnly = true)
    public List<PeriodStats> statsFor(Tenant club, Periodo periodo, LocalDate from, LocalDate to) {
        List<Court> courts = courtRepository.findAllByActiveTrueOrderByDisplayOrderAscNameAsc();
        LocalDate rangeEnd = to.plusDays(1);

        // Antes: un findBetween() y un findOverlapping() de blackouts por bucket
        // (hasta ~365 de cada uno para un año en DIA). El clamping de abajo ya
        // garantiza que ningun bucket sale de [from, rangeEnd), asi que alcanza
        // con traer ese rango una vez y filtrar cada bucket en memoria.
        Instant rangeFromInstant = from.atStartOfDay(club.zoneId()).toInstant();
        Instant rangeUntilInstant = rangeEnd.atStartOfDay(club.zoneId()).toInstant();
        List<BookingStatsRow> allBookings = bookingRepository.findStatsBetween(rangeFromInstant, rangeUntilInstant);
        List<Blackout> blackouts = blackoutRepository.findOverlapping(rangeFromInstant, rangeUntilInstant);
        // Los cobros van por su propia fecha, asi que se traen por el rango entero
        // y se reparten en buckets aparte de los turnos: un pago del rango puede
        // caer en un bucket distinto al del turno que lo origino.
        List<PaymentCashRow> allPayments = paymentRepository.findCashBetween(
                rangeFromInstant, rangeUntilInstant, PaymentStatus.APPROVED);

        List<PeriodStats> result = new ArrayList<>();
        for (LocalDate bucketStart = bucketStart(periodo, from); !bucketStart.isAfter(to);
                bucketStart = nextBucketStart(periodo, bucketStart)) {
            LocalDate bucketEnd = nextBucketStart(periodo, bucketStart);
            LocalDate queryFrom = bucketStart.isBefore(from) ? from : bucketStart;
            LocalDate queryTo = bucketEnd.isAfter(rangeEnd) ? rangeEnd : bucketEnd;
            Instant bucketFromInstant = queryFrom.atStartOfDay(club.zoneId()).toInstant();
            Instant bucketUntilInstant = queryTo.atStartOfDay(club.zoneId()).toInstant();

            List<BookingStatsRow> bookings = allBookings.stream()
                    .filter(b -> !b.startTime().isBefore(bucketFromInstant)
                            && b.startTime().isBefore(bucketUntilInstant))
                    .toList();
            List<PaymentCashRow> payments = allPayments.stream()
                    .filter(p -> !p.createdAt().isBefore(bucketFromInstant)
                            && p.createdAt().isBefore(bucketUntilInstant))
                    .toList();
            result.add(summarize(club, courts, queryFrom, queryTo, bookings, payments, blackouts,
                    label(periodo, bucketStart)));
        }
        return result;
    }

    /**
     * Resumen del rango completo tratado como un solo bloque, sin agrupar.
     *
     * <p>Son los datos de las tarjetas KPI: a diferencia de {@link #statsFor}, no
     * depende de que periodo (semana/mes/año) haya elegido el usuario para la
     * tabla de abajo.
     */
    @Transactional(readOnly = true)
    public PeriodStats summary(Tenant club, LocalDate from, LocalDate to) {
        List<Court> courts = courtRepository.findAllByActiveTrueOrderByDisplayOrderAscNameAsc();
        LocalDate toExclusive = to.plusDays(1);
        Instant fromInstant = from.atStartOfDay(club.zoneId()).toInstant();
        Instant untilInstant = toExclusive.atStartOfDay(club.zoneId()).toInstant();
        List<BookingStatsRow> bookings = bookingRepository.findStatsBetween(fromInstant, untilInstant);
        List<PaymentCashRow> payments = paymentRepository.findCashBetween(
                fromInstant, untilInstant, PaymentStatus.APPROVED);
        List<Blackout> blackouts = blackoutRepository.findOverlapping(fromInstant, untilInstant);
        return summarize(club, courts, from, toExclusive, bookings, payments, blackouts, "Total");
    }

    /** Los horarios que mas turnos venden en el rango, sin importar el dia. */
    @Transactional(readOnly = true)
    public List<HourlyStat> topHours(Tenant club, LocalDate from, LocalDate to, int limit) {
        Map<java.time.LocalTime, List<BookingStatsRow>> byHour = bookingsInRange(club, from, to).stream()
                .collect(Collectors.groupingBy(b -> b.startTime().atZone(club.zoneId()).toLocalTime()));

        return byHour.entrySet().stream()
                .map(entry -> new HourlyStat(entry.getKey(), entry.getValue().size(), facturado(entry.getValue())))
                .sorted(Comparator.comparingInt(HourlyStat::turnos).reversed())
                .limit(limit)
                .toList();
    }

    /**
     * Agrupa turnos para top clientes: por cuenta si el turno esta vinculado a
     * una (dos ids de cuenta iguales son la misma fila), por cliente si no.
     */
    private record CustomerGroupKey(UUID accountId, UUID customerId) {
    }

    /**
     * Los clientes que mas facturaron en el rango.
     *
     * <p>Se agrupa por cuenta cuando el turno la tiene vinculada ({@code
     * Booking#playerAccountId}), no por {@code Customer}: un mismo jugador
     * logueado puede haber reservado alguna vez con otro telefono y terminar
     * repartido en dos filas de "top clientes" que en realidad son la misma
     * persona. Sin cuenta vinculada (la mayoria de las reservas, que nunca
     * piden login) se sigue agrupando por cliente, como antes.
     *
     * <p>El nombre que se muestra para una cuenta es el actual
     * ({@code PlayerAccount#displayName}), no el que quedo guardado en el
     * {@code Customer} de la reserva mas vieja del grupo: si el jugador
     * corrigio su nombre despues, la lista tiene que reflejarlo.
     */
    @Transactional(readOnly = true)
    public List<CustomerStat> topCustomers(Tenant club, LocalDate from, LocalDate to, int limit) {
        // Se agrupa por id, no por el registro entero: dos filas del mismo cliente
        // difieren en horario/precio, asi que agrupar "por cliente" solo funciona
        // por su identidad, no por igualdad estructural de la proyeccion.
        Map<CustomerGroupKey, List<BookingStatsRow>> grouped = bookingsInRange(club, from, to).stream()
                .collect(Collectors.groupingBy(row -> row.playerAccountId() != null
                        ? new CustomerGroupKey(row.playerAccountId(), null)
                        : new CustomerGroupKey(null, row.customerId())));

        Set<UUID> accountIds = grouped.keySet().stream()
                .map(CustomerGroupKey::accountId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, PlayerAccount> accountsById = playerAccountRepository.findAllById(accountIds).stream()
                .collect(Collectors.toMap(PlayerAccount::getId, account -> account));

        return grouped.entrySet().stream()
                .map(entry -> {
                    List<BookingStatsRow> rows = entry.getValue();
                    BookingStatsRow first = rows.get(0);
                    PlayerAccount account = accountsById.get(entry.getKey().accountId());
                    String name = account != null && account.getDisplayName() != null
                            ? account.getDisplayName()
                            : first.customerFullName();
                    String phone = account != null && account.getPhoneNumber() != null
                            ? account.getPhoneNumber()
                            : first.customerPhoneNumber();
                    return new CustomerStat(name, phone, rows.size(), facturado(rows));
                })
                .sorted(Comparator.comparing(CustomerStat::facturado).reversed())
                .limit(limit)
                .toList();
    }

    /** Por que se cancelo cada turno anulado del rango: para ver si predomina un motivo. */
    @Transactional(readOnly = true)
    public List<CancellationStat> cancellationsByReason(Tenant club, LocalDate from, LocalDate to) {
        List<BookingStatsRow> cancelled = bookingRepository.findStatsBetween(
                        from.atStartOfDay(club.zoneId()).toInstant(),
                        nextDay(to).atStartOfDay(club.zoneId()).toInstant())
                .stream()
                .filter(b -> b.status() == BookingStatus.CANCELLED)
                .toList();

        // La columna es nullable en la base (V1__baseline.sql): el flujo normal
        // siempre pasa por Booking.markCancelled y carga un motivo, pero una fila
        // que entra por otro lado (una migracion, una correccion manual) puede no
        // traerlo. Collectors.groupingBy no admite un classifier que devuelva null
        // -tira NullPointerException y se lleva puesta toda la vista de
        // estadisticas, no solo esta tarjeta-, asi que se envuelve en Optional y se
        // agrupa bajo "sin motivo" en vez de filtrar la reserva: si se filtrara, el
        // desglose sumaria menos que el total de "Cancelados" de la tarjeta.
        Map<Optional<CancellationReason>, Long> byReason = cancelled.stream()
                .collect(Collectors.groupingBy(b -> Optional.ofNullable(b.cancellationReason()),
                        Collectors.counting()));

        return byReason.entrySet().stream()
                .map(entry -> new CancellationStat(entry.getKey().orElse(null), entry.getValue().intValue()))
                .sorted(Comparator.comparingInt(CancellationStat::count).reversed())
                .toList();
    }

    /**
     * Cuanto de lo cobrado en el rango entro en efectivo y cuanto por MercadoPago.
     *
     * <p>Mismo criterio que la tarjeta "Cobrado": por fecha del cobro y sobre los
     * mismos pagos, para que el desglose sume exactamente esa tarjeta.
     */
    @Transactional(readOnly = true)
    public List<PaymentMethodStat> paymentsByMethod(Tenant club, LocalDate from, LocalDate to) {
        Map<PaymentMethod, BigDecimal> byMethod = paymentRepository.findCashBetween(
                        from.atStartOfDay(club.zoneId()).toInstant(),
                        nextDay(to).atStartOfDay(club.zoneId()).toInstant(),
                        PaymentStatus.APPROVED).stream()
                .collect(Collectors.groupingBy(PaymentCashRow::method,
                        Collectors.reducing(BigDecimal.ZERO, PaymentCashRow::amount, BigDecimal::add)));

        return byMethod.entrySet().stream()
                .map(entry -> new PaymentMethodStat(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(PaymentMethodStat::total).reversed())
                .toList();
    }

    // ------------------------------------------------------------ internos

    /** Solo los turnos que de verdad ocuparon una cancha: sin cancelados ni vencidos. */
    private List<BookingStatsRow> bookingsInRange(Tenant club, LocalDate from, LocalDate to) {
        return bookingRepository.findStatsBetween(
                        from.atStartOfDay(club.zoneId()).toInstant(),
                        nextDay(to).atStartOfDay(club.zoneId()).toInstant())
                .stream()
                .filter(b -> b.status().occupiesSlot())
                .toList();
    }

    private LocalDate nextDay(LocalDate date) {
        return date.plusDays(1);
    }

    private PeriodStats summarize(Tenant club, List<Court> courts, LocalDate bucketStart,
                                  LocalDate bucketEndExclusive, List<BookingStatsRow> bookings,
                                  List<PaymentCashRow> payments, List<Blackout> blackouts,
                                  String label) {
        int jugados = 0;
        int reservados = 0;
        int cancelados = 0;
        int noShows = 0;
        BigDecimal facturado = BigDecimal.ZERO;
        // Lo cobrado sale de los pagos del periodo, no de sumar paid_amount de los
        // turnos: ese campo dice cuanto lleva pago un turno, no cuando entro esa
        // plata, y ademas se perdia entero si el turno terminaba cancelado.
        BigDecimal cobrado = payments.stream()
                .map(PaymentCashRow::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Duration horas = Duration.ZERO;
        Map<LocalDate, Integer> occupiedByDay = new HashMap<>();

        for (BookingStatsRow booking : bookings) {
            switch (booking.status()) {
                case COMPLETED -> jugados++;
                case CONFIRMED -> reservados++;
                case CANCELLED -> cancelados++;
                case NO_SHOW -> noShows++;
                default -> {
                    // DRAFT/AWAITING_CONFIRMATION: todavia no es ni un turno firme ni una
                    // baja, no suma a ninguna de las dos columnas.
                }
            }
            if (booking.status().occupiesSlot()) {
                horas = horas.plus(booking.duration());
                facturado = facturado.add(booking.totalPrice());
                LocalDate day = booking.startTime().atZone(club.zoneId()).toLocalDate();
                occupiedByDay.merge(day, 1, Integer::sum);
            }
        }

        Occupancy occupancy = occupancy(club, courts, bucketStart, bucketEndExclusive, occupiedByDay, blackouts);
        return new PeriodStats(label, bucketStart, bucketEndExclusive.minusDays(1), jugados, horas,
                reservados, facturado, cobrado, cancelados, noShows,
                occupancy.occupied(), occupancy.capacity(), occupancy.percentage());
    }

    /** Turnos ocupados, turnos posibles y el porcentaje entre los dos, ya redondeado. */
    private record Occupancy(int occupied, int capacity, BigDecimal percentage) {
    }

    /**
     * Suma turnos ocupados y turnos posibles dia por dia del bucket.
     *
     * <p>Mismo calculo que ya hace {@code AgendaView.summaryOf} para un solo dia
     * (turnos ocupados sobre slots posibles), extendido a varios dias. Como la
     * capacidad diaria es la misma todos los dias (mismas canchas, misma grilla),
     * sumar y despues dividir da el mismo porcentaje que promediar dia por dia, y
     * de paso deja los dos numeros crudos para mostrar "10/125" en vez de solo "8%".
     */
    private Occupancy occupancy(Tenant club, List<Court> courts, LocalDate from,
                                LocalDate toExclusive, Map<LocalDate, Integer> occupiedByDay,
                                List<Blackout> blackouts) {
        if (courts.isEmpty()) {
            return new Occupancy(0, 0, BigDecimal.ZERO);
        }
        int totalOccupied = 0;
        int totalCapacity = 0;
        for (LocalDate day = from; day.isBefore(toExclusive); day = day.plusDays(1)) {
            // Un dia suspendido para todas las canchas no suma capacidad: nadie
            // podia reservar ahi, asi que tampoco deberia contar como "posible".
            // Una suspension parcial (una cancha, una franja) se deja pasar a
            // proposito: separar la capacidad por cancha es un cambio mas grande
            // que lo que esta cuenta necesita.
            if (!dayFullyBlocked(club, day, blackouts)) {
                totalCapacity += slotGenerator.generate(club, day).size() * courts.size();
            }
            totalOccupied += occupiedByDay.getOrDefault(day, 0);
        }
        BigDecimal percentage = totalCapacity == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(totalOccupied)
                        .divide(BigDecimal.valueOf(totalCapacity), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP);
        return new Occupancy(totalOccupied, totalCapacity, percentage);
    }

    /** Si hay un blackout de club entero que cubre todo el dia operativo. */
    private boolean dayFullyBlocked(Tenant club, LocalDate day, List<Blackout> blackouts) {
        Instant dayStart = slotGenerator.dayStart(club, day);
        Instant dayEnd = slotGenerator.dayEnd(club, day);
        return blackouts.stream()
                .anyMatch(blackout -> blackout.getCourt() == null
                        && !blackout.getStartTime().isAfter(dayStart)
                        && !blackout.getEndTime().isBefore(dayEnd));
    }

    private BigDecimal facturado(List<BookingStatsRow> bookings) {
        return bookings.stream().map(BookingStatsRow::totalPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private LocalDate bucketStart(Periodo periodo, LocalDate date) {
        return switch (periodo) {
            case DIA -> date;
            case SEMANA -> date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
            case MES -> date.withDayOfMonth(1);
            case ANIO -> date.withDayOfYear(1);
        };
    }

    private LocalDate nextBucketStart(Periodo periodo, LocalDate bucketStart) {
        return switch (periodo) {
            case DIA -> bucketStart.plusDays(1);
            case SEMANA -> bucketStart.plusWeeks(1);
            case MES -> bucketStart.plusMonths(1);
            case ANIO -> bucketStart.plusYears(1);
        };
    }

    private String label(Periodo periodo, LocalDate bucketStart) {
        return switch (periodo) {
            case DIA -> capitalize(DAY_LABEL.format(bucketStart));
            case SEMANA -> "Semana del " + SHORT_DATE.format(bucketStart);
            case MES -> capitalize(MONTH_YEAR.format(bucketStart));
            case ANIO -> String.valueOf(bucketStart.getYear());
        };
    }

    /** "septiembre 2026" -> "Septiembre 2026": el mes de un formatter siempre sale en minuscula. */
    private String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}

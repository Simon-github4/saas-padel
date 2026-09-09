package ar.com.padelnec.perf;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.LongAdder;

/**
 * Pega contra {@code GET /api/public/{slug}/availability} con concurrencia fija
 * durante N segundos y reporta throughput y percentiles de latencia.
 *
 * <p>No es un {@code @Test}: el nombre queda deliberadamente fuera del patron que
 * usa Surefire ({@code **&#47;*Test.java}), asi que {@code mvn test} nunca lo corre.
 * Es para pararse al lado de un {@code mvn spring-boot:run -Dspring-boot.run.profiles=dev}
 * (que ya siembra el club {@code club-necochea}) y confirmar a mano que la grilla
 * publica responde rapido con carga real, antes y despues de un cambio.
 *
 * <p>Uso:
 * <pre>
 * mvn test-compile
 * java -cp target/classes;target/test-classes ar.com.padelnec.perf.AvailabilityLoadTool \
 *     http://localhost:8080 club-necochea 2026-09-15 20 30
 * </pre>
 * (baseUrl, slug, fecha, usuarios concurrentes, segundos — todos opcionales, con
 * defaults razonables si se omiten de derecha a izquierda).
 */
public final class AvailabilityLoadTool {

    public static void main(String[] args) throws Exception {
        String baseUrl = args.length > 0 ? args[0] : "http://localhost:8080";
        String slug = args.length > 1 ? args[1] : "club-necochea";
        String date = args.length > 2 ? args[2] : LocalDate.now().plusDays(1).toString();
        int concurrency = args.length > 3 ? Integer.parseInt(args[3]) : 20;
        int seconds = args.length > 4 ? Integer.parseInt(args[4]) : 30;

        System.out.printf("Pegando a %s/api/public/%s/availability?date=%s con %d usuarios durante %ds%n",
                baseUrl, slug, date, concurrency, seconds);

        URI uri = URI.create(baseUrl + "/api/public/" + slug + "/availability?date=" + date);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

        LongAdder ok = new LongAdder();
        LongAdder failed = new LongAdder();
        List<Long> latenciesMs = Collections.synchronizedList(new ArrayList<>());
        Instant deadline = Instant.now().plusSeconds(seconds);

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> workers = new ArrayList<>();
            for (int i = 0; i < concurrency; i++) {
                workers.add(pool.submit(() -> {
                    HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
                    while (Instant.now().isBefore(deadline)) {
                        long start = System.nanoTime();
                        try {
                            HttpResponse<Void> response =
                                    client.send(request, HttpResponse.BodyHandlers.discarding());
                            latenciesMs.add((System.nanoTime() - start) / 1_000_000);
                            (response.statusCode() == 200 ? ok : failed).increment();
                        } catch (Exception ex) {
                            failed.increment();
                        }
                    }
                }));
            }
            for (Future<?> worker : workers) {
                worker.get();
            }
        }
        report(ok.sum(), failed.sum(), seconds, latenciesMs);
    }

    private static void report(long ok, long failed, int seconds, List<Long> latenciesMs) {
        List<Long> sorted = new ArrayList<>(latenciesMs);
        Collections.sort(sorted);
        System.out.printf("Requests: %d ok, %d failed, %.1f req/s%n",
                ok, failed, (ok + failed) / (double) seconds);
        if (!sorted.isEmpty()) {
            System.out.printf("Latencia p50=%dms p90=%dms p99=%dms max=%dms%n",
                    percentile(sorted, 50), percentile(sorted, 90),
                    percentile(sorted, 99), sorted.get(sorted.size() - 1));
        }
    }

    private static long percentile(List<Long> sorted, int p) {
        int index = Math.min(sorted.size() - 1, (int) Math.ceil(p / 100.0 * sorted.size()) - 1);
        return sorted.get(Math.max(0, index));
    }

    private AvailabilityLoadTool() {
    }
}

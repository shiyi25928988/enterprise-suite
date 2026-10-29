import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * M6b raw-read bench: concurrent stateless GETs to measure container/HTTP
 * throughput (no auth, no session writes).
 *
 *   java bench/GetBench.java http://localhost:9000/.well-known/openid-configuration 64 2000
 */
public class GetBench {

    public static void main(String[] args) throws Exception {
        String url = args.length > 0 ? args[0] : "http://localhost:9000/.well-known/openid-configuration";
        int concurrency = args.length > 1 ? Integer.parseInt(args[1]) : 64;
        int perWorker = args.length > 2 ? Integer.parseInt(args[2]) : 2000;

        AtomicLong ok = new AtomicLong();
        AtomicLong errors = new AtomicLong();
        List<Long> latencies = java.util.Collections.synchronizedList(new ArrayList<>());

        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        long start = System.nanoTime();
        try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
            var done = new CountDownLatch(concurrency);
            for (int w = 0; w < concurrency; w++) {
                int worker = w;
                ex.submit(() -> {
                    try {
                        var req = HttpRequest.newBuilder(URI.create(url)).GET().build();
                        for (int i = 0; i < perWorker; i++) {
                            long t0 = System.nanoTime();
                            try {
                                var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                                if (resp.statusCode() == 200) {
                                    ok.incrementAndGet();
                                    latencies.add(System.nanoTime() - t0);
                                } else {
                                    errors.incrementAndGet();
                                }
                            } catch (Exception e) {
                                errors.incrementAndGet();
                            }
                        }
                    } finally {
                        done.countDown();
                    }
                });
            }
            done.await();
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        List<Long> sorted = new ArrayList<>(latencies);
        sorted.sort(Comparator.naturalOrder());

        System.out.printf("""
                        ======================= GetBench =======================
                        url         : %s
                        requests    : %,d (OK %,d, errors %,d)
                        elapsed     : %,d ms
                        throughput  : %,.0f req/s
                        latency     : P50 %,d ms | P95 %,d ms | P99 %,d ms
                        ========================================================
                        """,
                url, (long) concurrency * perWorker, ok.get(), errors.get(), elapsedMs,
                elapsedMs > 0 ? ok.get() * 1000.0 / elapsedMs : 0,
                pct(sorted, 0.50), pct(sorted, 0.95), pct(sorted, 0.99));
    }

    private static long pct(List<Long> sortedNanos, double p) {
        if (sortedNanos.isEmpty()) return 0;
        int idx = (int) Math.min(sortedNanos.size() - 1, Math.floor(p * sortedNanos.size()));
        return sortedNanos.get(idx) / 1_000_000;
    }
}

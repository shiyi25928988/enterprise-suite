import java.net.CookieManager;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * M6b load bench: mixed login + /api/auth/me traffic against a running
 * auth-server. Single-file source — run directly on JDK 21:
 *
 * <pre>
 *   java bench/LoginBench.java http://localhost:9000 32 200 admin admin123
 *        concurrency=32, iterations per worker=200 => 6400 flows
 * </pre>
 *
 * Each iteration: GET /api/auth/csrf -> POST /api/auth/login -> GET /api/auth/me.
 * Uses virtual threads; cookie state is per-worker via a per-thread CookieManager.
 */
public class LoginBench {

    public static void main(String[] args) throws Exception {
        String base = args.length > 0 ? args[0] : "http://localhost:9000";
        int concurrency = args.length > 1 ? Integer.parseInt(args[1]) : 32;
        int iterations = args.length > 2 ? Integer.parseInt(args[2]) : 200;
        String user = args.length > 3 ? args[3] : "admin";
        String pass = args.length > 4 ? args[4] : "admin123";

        AtomicLong ok = new AtomicLong();
        AtomicLong loginFail = new AtomicLong();
        AtomicLong errors = new AtomicLong();
        List<Long> flowNanos = java.util.Collections.synchronizedList(new ArrayList<>());

        long startNanos = System.nanoTime();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var done = new CountDownLatch(concurrency);
            for (int w = 0; w < concurrency; w++) {
                executor.submit(() -> {
                    try {
                        runWorker(base, iterations, user, pass, ok, loginFail, errors, flowNanos);
                    } catch (Exception e) {
                        errors.addAndGet(iterations);
                    } finally {
                        done.countDown();
                    }
                });
            }
            done.await();
        }
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

        long flows = ok.get();
        List<Long> sorted = new ArrayList<>(flowNanos);
        sorted.sort(Comparator.naturalOrder());
        System.out.printf("""
                        ===================== LoginBench =====================
                        target            : %s
                        workers x iters   : %d x %d = %d flows
                        flows OK          : %d  (login failures: %d, hard errors: %d)
                        elapsed           : %,d ms
                        throughput        : %,.0f flows/s  (3 HTTP requests each)
                        latency per flow  : P50 %,d ms | P95 %,d ms | P99 %,d ms | max %,d ms
                        ======================================================
                        """,
                base, concurrency, iterations, (long) concurrency * iterations,
                flows, loginFail.get(), errors.get(), elapsedMillis,
                elapsedMillis > 0 ? flows * 1000.0 / elapsedMillis : 0,
                pct(sorted, 0.50), pct(sorted, 0.95), pct(sorted, 0.99),
                sorted.isEmpty() ? 0 : sorted.getLast() / 1_000_000);
    }

    private static void runWorker(String base, int iterations, String user, String pass,
                                  AtomicLong ok, AtomicLong loginFail, AtomicLong errors,
                                  List<Long> flowNanos) throws Exception {
        var cookies = new CookieManager();
        var client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        // spread workers over distinct usernames? no — same admin, but jitter start
        Thread.sleep(ThreadLocalRandom.current().nextLong(50));

        for (int i = 0; i < iterations; i++) {
            long t0 = System.nanoTime();
            try {
                // 1) csrf
                var csrfResp = client.send(get(base + "/api/auth/csrf"), HttpResponse.BodyHandlers.ofString());
                String token = extract(csrfResp.body(), "token");
                if (token == null) { errors.incrementAndGet(); continue; }

                // 2) login
                var loginReq = HttpRequest.newBuilder(URI.create(base + "/api/auth/login"))
                        .header("Content-Type", "application/json")
                        .header("X-XSRF-TOKEN", token)
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}"))
                        .build();
                var loginResp = client.send(loginReq, HttpResponse.BodyHandlers.ofString());
                if (loginResp.statusCode() != 200) { loginFail.incrementAndGet(); continue; }

                // 3) me
                var meResp = client.send(get(base + "/api/auth/me"), HttpResponse.BodyHandlers.ofString());
                if (meResp.statusCode() != 200 || !meResp.body().contains("\"success\":true")) {
                    loginFail.incrementAndGet(); continue;
                }
                // logout so sessions don't accumulate in Redis during the run
                var logoutReq = HttpRequest.newBuilder(URI.create(base + "/api/auth/logout"))
                        .header("X-XSRF-TOKEN", token)
                        .POST(HttpRequest.BodyPublishers.noBody()).build();
                client.send(logoutReq, HttpResponse.BodyHandlers.ofString());

                ok.incrementAndGet();
                flowNanos.add(System.nanoTime() - t0);
            } catch (Exception e) {
                errors.incrementAndGet();
            }
        }
    }

    private static HttpRequest get(String url) {
        return HttpRequest.newBuilder(URI.create(url)).GET().build();
    }

    private static String extract(String json, String field) {
        var m = java.util.regex.Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static long pct(List<Long> sortedNanos, double p) {
        if (sortedNanos.isEmpty()) return 0;
        int idx = (int) Math.min(sortedNanos.size() - 1, Math.floor(p * sortedNanos.size()));
        return sortedNanos.get(idx) / 1_000_000;
    }
}

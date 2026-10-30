package com.nexo.app.shared.infrastructure.observability;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Bounded, process-local evidence of technical failures observed on normal
 * application traffic.
 *
 * <p>This type deliberately has no persistence or network dependency. It is
 * safe to call from request filters and asynchronous jobs. Only the module,
 * HTTP method and a normalized route are retained; user identifiers never
 * become metric dimensions.</p>
 */
@Component
public class PassiveHealthMetrics {

    public static final Duration WINDOW = Duration.ofMinutes(5);
    public static final int MINIMUM_OPERATIONS = 20;
    public static final double MINIMUM_FAILURE_RATE = 0.05d;
    static final int MAX_TRACKED_SERIES = 512;
    private static final Duration BUCKET_SIZE = Duration.ofSeconds(1);
    private static final int MAX_ROUTE_LENGTH = 128;
    private static final Pattern UUID = Pattern.compile(
            "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    private static final Pattern NUMERIC = Pattern.compile("[0-9]{1,20}");
    private static final Pattern ULID_OR_TOKEN = Pattern.compile("(?i)[0-9a-z]{20,}");
    private static final Set<String> MODULES = Set.of(
            "iam", "rh", "estoque", "contrato", "execucao", "dashboard",
            "financeiro", "desconto", "obras", "sst", "chamado", "notificacao",
            "mencao", "lookup", "assistente");

    private final Map<SeriesKey, WindowCounter> counters = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Object admissionLock = new Object();

    public PassiveHealthMetrics() {
        this(Clock.systemUTC());
    }

    public PassiveHealthMetrics(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Records the outcome of an HTTP operation without retaining its body or user. */
    public void recordHttpOutcome(String module, String method, String route, int statusCode) {
        Outcome outcome = statusCode >= 500
                ? Outcome.TECHNICAL_ERROR
                : statusCode >= 400 ? Outcome.EXPECTED_CLIENT_ERROR : Outcome.SUCCESS;
        record(module, routeKey(method, route), outcome);
    }

    /** Records an explicitly classified asynchronous operation. */
    public void record(String module, String route, Outcome outcome) {
        String canonicalModule = canonicalModule(module);
        if (canonicalModule == null || outcome == null) {
            return;
        }
        String canonicalRoute = normalizeRoute(route);
        SeriesKey key = new SeriesKey(canonicalModule, canonicalRoute);
        WindowCounter counter = counters.get(key);
        if (counter == null) {
            synchronized (admissionLock) {
                counter = counters.get(key);
                if (counter == null && counters.size() < MAX_TRACKED_SERIES) {
                    counter = new WindowCounter();
                    counters.put(key, counter);
                }
            }
        }
        if (counter != null) {
            counter.record(Instant.now(clock), outcome == Outcome.TECHNICAL_ERROR);
        }
    }

    /** Convenience API for asynchronous jobs that already know the result is technical. */
    public void recordTechnicalError(String module, String route) {
        record(module, route, Outcome.TECHNICAL_ERROR);
    }

    /** Convenience API for a successful asynchronous operation. */
    public void recordSuccess(String module, String route) {
        record(module, route, Outcome.SUCCESS);
    }

    /** Returns aggregate evidence for all normalized routes in a module. */
    public Snapshot snapshot(String module) {
        String canonicalModule = canonicalModule(module);
        if (canonicalModule == null) {
            return Snapshot.empty(module, Instant.now(clock));
        }
        Instant now = Instant.now(clock);
        long operations = 0;
        long technicalErrors = 0;
        for (Map.Entry<SeriesKey, WindowCounter> entry : counters.entrySet()) {
            if (entry.getKey().module().equals(canonicalModule)) {
                Counts counts = entry.getValue().snapshot(now);
                operations += counts.operations();
                technicalErrors += counts.technicalErrors();
            }
        }
        return new Snapshot(canonicalModule, operations, technicalErrors, now);
    }

    /** Returns evidence for one normalized route, useful for diagnostics and tests. */
    public Snapshot snapshot(String module, String route) {
        String canonicalModule = canonicalModule(module);
        String canonicalRoute = normalizeRoute(route);
        WindowCounter counter = canonicalModule == null ? null
                : counters.get(new SeriesKey(canonicalModule, canonicalRoute));
        Counts counts = counter == null ? Counts.EMPTY : counter.snapshot(Instant.now(clock));
        return new Snapshot(canonicalModule == null ? module : canonicalModule,
                counts.operations(), counts.technicalErrors(), Instant.now(clock));
    }

    public boolean isDegraded(String module) {
        return snapshot(module).degraded();
    }

    public int trackedSeriesCount() {
        return counters.size();
    }

    /** Normalizes method/path into a bounded series key. */
    public static String routeKey(String method, String route) {
        String verb = method == null || method.isBlank() ? "*" : method.trim().toUpperCase(Locale.ROOT);
        return verb + " " + normalizeRoute(route);
    }

    /**
     * Removes context/query strings, collapses slashes and replaces identifiers
     * with a stable placeholder. The result is deliberately bounded.
     */
    public static String normalizeRoute(String route) {
        if (route == null || route.isBlank()) {
            return "/unknown";
        }
        String path = route.trim();
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        path = path.replaceAll("/{2,}", "/");
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.equals("/api")) {
            path = "/";
        } else if (path.startsWith("/api/")) {
            path = path.substring(4);
        }
        StringBuilder normalized = new StringBuilder(path.length());
        for (String segment : path.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            String normalizedSegment = normalizeSegment(segment);
            int available = MAX_ROUTE_LENGTH - normalized.length() - 1;
            if (available <= 0) {
                break;
            }
            normalized.append('/').append(normalizedSegment, 0,
                    Math.min(available, normalizedSegment.length()));
        }
        return normalized.isEmpty() ? "/" : normalized.toString();
    }

    private static String normalizeSegment(String segment) {
        if (UUID.matcher(segment).matches() || NUMERIC.matcher(segment).matches()
                || ULID_OR_TOKEN.matcher(segment).matches()) {
            return "{id}";
        }
        String safe = segment.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._~-]", "_");
        return safe.length() > 48 ? safe.substring(0, 48) : safe;
    }

    private static String canonicalModule(String module) {
        if (module == null || module.isBlank()) {
            return null;
        }
        String canonical = module.trim().toLowerCase(Locale.ROOT);
        return MODULES.contains(canonical) ? canonical : null;
    }

    private static final class WindowCounter {
        private final Deque<Bucket> buckets = new ArrayDeque<>();

        synchronized void record(Instant timestamp, boolean technicalError) {
            purge(timestamp);
            long second = timestamp.getEpochSecond();
            Bucket bucket = buckets.peekLast();
            if (bucket == null || bucket.epochSecond() != second) {
                bucket = new Bucket(second);
                buckets.addLast(bucket);
            }
            bucket.operations++;
            if (technicalError) {
                bucket.technicalErrors++;
            }
        }

        synchronized Counts snapshot(Instant now) {
            purge(now);
            long operations = 0;
            long errors = 0;
            for (Bucket bucket : buckets) {
                operations += bucket.operations;
                errors += bucket.technicalErrors;
            }
            return new Counts(operations, errors);
        }

        private void purge(Instant now) {
            Instant cutoff = now.minus(WINDOW);
            while (!buckets.isEmpty()) {
                Bucket first = buckets.peekFirst();
                Instant bucketEnd = Instant.ofEpochSecond(first.epochSecond()).plus(BUCKET_SIZE);
                if (bucketEnd.isAfter(cutoff)) {
                    break;
                }
                buckets.removeFirst();
            }
        }
    }

    private static final class Bucket {
        private final long epochSecond;
        private long operations;
        private long technicalErrors;

        private Bucket(long epochSecond) {
            this.epochSecond = epochSecond;
        }

        long epochSecond() {
            return epochSecond;
        }
    }

    public enum Outcome {
        SUCCESS,
        EXPECTED_CLIENT_ERROR,
        DOMAIN_ERROR,
        TECHNICAL_ERROR
    }

    public record Snapshot(String module, long operations, long technicalErrors, Instant observedAt) {
        public Snapshot {
            Objects.requireNonNull(observedAt, "observedAt");
            if (operations < 0 || technicalErrors < 0 || technicalErrors > operations) {
                throw new IllegalArgumentException("contadores passivos inválidos");
            }
        }

        public static Snapshot empty(String module, Instant observedAt) {
            return new Snapshot(module, 0, 0, observedAt);
        }

        public long successfulOperations() {
            return operations - technicalErrors;
        }

        public double failureRate() {
            return operations == 0 ? 0d : (double) technicalErrors / operations;
        }

        public boolean hasMinimumSample() {
            return operations >= MINIMUM_OPERATIONS;
        }

        public boolean degraded() {
            return hasMinimumSample() && failureRate() >= MINIMUM_FAILURE_RATE;
        }
    }

    private record SeriesKey(String module, String route) { }

    private record Counts(long operations, long technicalErrors) {
        private static final Counts EMPTY = new Counts(0, 0);
    }
}

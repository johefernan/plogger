import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;

import java.util.Locale;
import java.util.Random;

public final class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private static final String[] LEVELS = {
            "TRACE", "DEBUG", "INFO", "WARN", "ERROR", "CRITICAL", "FATAL"
    };
    private static final int[] WEIGHTS = {5, 15, 62, 12, 5, 1, 0};
    private static final int WEIGHT_TOTAL = 100;

    private static final String[][] ROUTES = {
            {"GET", "/v1/users/{id}"},
            {"POST", "/v1/orders"},
            {"GET", "/v1/orders/{id}"},
            {"PUT", "/v1/users/{id}"},
            {"PATCH", "/v1/orders/{id}"},
            {"DELETE", "/v1/sessions/{id}"},
            {"GET", "/v1/reports/{id}"},
            {"POST", "/v1/notifications"},
    };
    private Main() {
    }

    public static void main(String[] args) throws InterruptedException {
        Random rng = new Random();
        long intervalMs = parseDurationToMillis(System.getenv("LOG_INTERVAL"), 1000L);
        int totalLogs = parseInt(System.getenv("TOTAL_LOGS"), -1);

        int seq = 1;
        Request request = null;
        while (totalLogs == -1 || seq <= totalLogs) {
            if (request == null || request.remaining <= 0) {
                request = newRequest(rng);
            }
            String severity = pickWeighted(rng);
            request.remaining--;
            boolean closing = request.remaining <= 0;
            if ("WARN".equals(severity) || "ERROR".equals(severity) || "CRITICAL".equals(severity)) {
                request.remaining = 0;
                closing = true;
            }

            LogEvent event = buildEvent(severity, request, closing, rng);
            LoggingEventBuilder builder = LOGGER.atLevel(toSlf4jLevel(severity))
                    .addKeyValue("language", "java")
                    .addKeyValue("severity", severity)
                    .addKeyValue("seq", seq)
                    .addKeyValue("request_id", request.requestId)
                    .addKeyValue("component", event.component)
                    .addKeyValue("method", request.method)
                    .addKeyValue("path", request.path);
            if (event.status != null) {
                builder = builder.addKeyValue("status", event.status);
            }
            if (event.durationMs != null) {
                builder = builder.addKeyValue("duration_ms", event.durationMs);
            }
            if (event.error != null) {
                builder = builder.addKeyValue("error", event.error);
            }
            builder.log(event.message);
            seq++;
            Thread.sleep(intervalMs);
        }
    }

    private static String pickWeighted(Random rng) {
        int roll = rng.nextInt(WEIGHT_TOTAL);
        int cursor = 0;
        for (int i = 0; i < LEVELS.length; i++) {
            cursor += WEIGHTS[i];
            if (roll < cursor) {
                return LEVELS[i];
            }
        }
        return "INFO";
    }

    private static Request newRequest(Random rng) {
        String[] route = ROUTES[rng.nextInt(ROUTES.length)];
        int resourceId = 1000 + rng.nextInt(9000);
        Request request = new Request();
        request.requestId = 100000 + rng.nextInt(900000);
        request.method = route[0];
        request.path = route[1].replace("{id}", Integer.toString(resourceId));
        request.resourceId = resourceId;
        request.remaining = 2 + rng.nextInt(4);
        return request;
    }

    private static LogEvent buildEvent(String level, Request request, boolean closing, Random rng) {
        String key = "user:" + request.resourceId;
        String table = request.path.split("/")[2];
        int jobId = 10000 + rng.nextInt(90000);
        LogEvent event = new LogEvent();

        switch (level) {
            case "TRACE" -> {
                switch (rng.nextInt(4)) {
                    case 0 -> {
                        event.component = "api";
                        event.message = String.format(
                                Locale.ROOT,
                                "accepted connection remote=10.%d.%d.%d protocol=http/1.1 bytes_in=%d",
                                rng.nextInt(256), rng.nextInt(256), 1 + rng.nextInt(254), 128 + rng.nextInt(8065));
                    }
                    case 1 -> {
                        event.component = "cache";
                        event.message = "cache lookup key=" + key + " namespace=app";
                    }
                    case 2 -> {
                        event.component = "db";
                        event.message = String.format(
                                Locale.ROOT,
                                "acquired connection pool=primary active=%d idle=%d wait_ms=%d",
                                1 + rng.nextInt(12), rng.nextInt(9), rng.nextInt(16));
                    }
                    default -> {
                        event.component = "worker";
                        event.message = "job dequeued queue=notifications job_id=" + jobId + " attempt=1";
                    }
                }
            }
            case "DEBUG" -> {
                switch (rng.nextInt(4)) {
                    case 0 -> {
                        event.component = "api";
                        event.message = String.format(
                                Locale.ROOT,
                                "parsed request body bytes=%d content_type=application/json fields=%d",
                                200 + rng.nextInt(11801), 2 + rng.nextInt(17));
                    }
                    case 1 -> {
                        event.component = "cache";
                        event.message = String.format(
                                Locale.ROOT,
                                "cache entry fresh key=%s ttl_s=%d bytes=%d",
                                key, 5 + rng.nextInt(296), 64 + rng.nextInt(4033));
                    }
                    case 2 -> {
                        event.component = "db";
                        event.message = String.format(
                                Locale.ROOT,
                                "executing query table=%s bind_params=%d statement_id=q_%d",
                                table, 1 + rng.nextInt(6), 100 + rng.nextInt(900));
                    }
                    default -> {
                        event.component = "worker";
                        event.message = String.format(
                                Locale.ROOT,
                                "rendered template channel=email template=order_update bytes=%d",
                                400 + rng.nextInt(7601));
                    }
                }
            }
            case "INFO" -> {
                if (closing) {
                    event.component = "api";
                    event.message = "request completed";
                    if ("DELETE".equals(request.method)) {
                        event.status = 204;
                    } else if ("POST".equals(request.method)) {
                        event.status = 201;
                    } else {
                        event.status = 200;
                    }
                    event.durationMs = String.format(Locale.ROOT, "%.2f", 8 + rng.nextDouble() * 372);
                } else {
                    switch (rng.nextInt(4)) {
                        case 0 -> {
                            event.component = "api";
                            event.message = "dispatching handler handler=" + request.method.toLowerCase(Locale.ROOT) + "_" + table;
                        }
                        case 1 -> {
                            event.component = "cache";
                            event.message = String.format(Locale.ROOT, "cache updated key=%s ttl_s=300 bytes=%d", key, 64 + rng.nextInt(4033));
                        }
                        case 2 -> {
                            event.component = "db";
                            event.message = String.format(Locale.ROOT, "loaded record table=%s id=%d source=primary", table, request.resourceId);
                        }
                        default -> {
                            event.component = "worker";
                            event.message = String.format(
                                    Locale.ROOT,
                                    "notification queued channel=email recipient_id=%d job_id=%d",
                                    request.resourceId, jobId);
                        }
                    }
                }
            }
            case "WARN" -> {
                switch (rng.nextInt(4)) {
                    case 0 -> {
                        double duration = 500 + rng.nextDouble() * 3500;
                        event.component = "db";
                        event.durationMs = String.format(Locale.ROOT, "%.2f", duration);
                        event.message = String.format(Locale.ROOT, "slow query duration_ms=%.0f threshold_ms=500 table=%s", duration, table);
                    }
                    case 1 -> {
                        event.component = "api";
                        event.message = String.format(
                                Locale.ROOT,
                                "retrying upstream attempt=%d max=3 reason=connection reset service=payments",
                                2 + rng.nextInt(2));
                    }
                    case 2 -> {
                        event.component = "cache";
                        event.message = "cache miss, reading from database key=" + key;
                    }
                    default -> {
                        event.component = "api";
                        event.message = "deprecated field user_id ignored; client should send id";
                    }
                }
            }
            case "ERROR" -> {
                switch (rng.nextInt(5)) {
                    case 0 -> {
                        event.component = "api";
                        event.status = 503;
                        event.error = "upstream 5xx";
                        event.message = "upstream request failed service=payments status=503";
                    }
                    case 1 -> {
                        event.component = "db";
                        event.error = "connection timeout";
                        event.message = "connection timeout service=postgres timeout_ms=5000";
                    }
                    case 2 -> {
                        event.component = "api";
                        event.status = 403;
                        event.error = "permission denied";
                        event.message = "permission denied user=u_" + request.resourceId + " action=update resource=orders";
                    }
                    case 3 -> {
                        event.component = "api";
                        event.status = 404;
                        event.error = "resource not found";
                        event.message = "resource not found id=" + request.resourceId;
                    }
                    default -> {
                        event.component = "api";
                        event.status = 401;
                        event.error = "invalid credentials";
                        event.message = "rejected credentials realm=api client_id=api_client";
                    }
                }
            }
            default -> {
                if (rng.nextInt(2) == 0) {
                    event.component = "db";
                    event.error = "connection timeout";
                    event.message = "database pool exhausted active=20 max=20 waiting=" + (4 + rng.nextInt(37));
                } else {
                    event.component = "api";
                    event.error = "upstream 5xx";
                    event.message = "authentication backend unavailable consecutive_failures="
                            + (5 + rng.nextInt(26)) + " dependency=auth";
                }
            }
        }
        return event;
    }

    private static Level toSlf4jLevel(String level) {
        return switch (level) {
            case "TRACE" -> Level.TRACE;
            case "DEBUG" -> Level.DEBUG;
            case "INFO" -> Level.INFO;
            case "WARN" -> Level.WARN;
            default -> Level.ERROR;
        };
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static long parseDurationToMillis(String value, long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        try {
            if (v.endsWith("ms")) {
                return Long.parseLong(v.substring(0, v.length() - 2).trim());
            }
            if (v.endsWith("s")) {
                return Long.parseLong(v.substring(0, v.length() - 1).trim()) * 1000L;
            }
            if (v.endsWith("m")) {
                return Long.parseLong(v.substring(0, v.length() - 1).trim()) * 60_000L;
            }
            if (v.endsWith("h")) {
                return Long.parseLong(v.substring(0, v.length() - 1).trim()) * 3_600_000L;
            }
        } catch (NumberFormatException ex) {
            return fallback;
        }
        return fallback;
    }

    private static final class Request {
        int requestId;
        String method;
        String path;
        int resourceId;
        int remaining;
    }

    private static final class LogEvent {
        String message;
        String component;
        Integer status;
        String durationMs;
        String error;
    }
}

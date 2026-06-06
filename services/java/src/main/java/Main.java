import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.util.Locale;
import java.util.Random;

public final class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    private static final String[] LEVELS = {"TRACE", "DEBUG", "INFO", "WARN", "ERROR", "CRITICAL", "FATAL"};
    private static final String[] ACTIONS = {
            "processing request", "connecting to database", "fetching user data",
            "updating cache", "validating input", "generating report", "sending notification"
    };
    private static final String[] USERS = {"user123", "admin", "guest", "service_account", "api_client"};
    private static final String[] ERRORS = {
            "connection timeout", "invalid credentials", "resource not found",
            "permission denied", "internal server error"
    };

    public static void main(String[] args) throws InterruptedException {
        Random rng = new Random();
        long intervalMs = parseDurationToMillis(System.getenv("LOG_INTERVAL"), 1000L);
        int totalLogs = parseInt(System.getenv("TOTAL_LOGS"), -1);

        int seq = 1;
        while (totalLogs == -1 || seq <= totalLogs) {
            String level = LEVELS[rng.nextInt(LEVELS.length)];
            String message = buildMessage(level, rng);

            var event = LOGGER.atLevel(toSlf4jLevel(level))
                    .addKeyValue("language", "java")
                    .addKeyValue("seq", seq);

            if (rng.nextFloat() < 0.5f) {
                event = event.addKeyValue("request_id", rng.nextInt(10_000));
            }
            if (rng.nextFloat() < 0.3f) {
                event = event.addKeyValue("duration_ms", String.format(Locale.ROOT, "%.2f", rng.nextDouble() * 1000.0));
            }

            event.log(message);
            seq++;
            Thread.sleep(intervalMs);
        }
    }

    private static String buildMessage(String level, Random rng) {
        String action = ACTIONS[rng.nextInt(ACTIONS.length)];
        String user = USERS[rng.nextInt(USERS.length)];
        String error = ERRORS[rng.nextInt(ERRORS.length)];

        return switch (level) {
            case "TRACE" -> "trace " + action;
            case "DEBUG" -> "debug " + action;
            case "INFO" -> "info " + action + " user=" + user;
            case "WARN" -> "warn possible issue action=" + action;
            default -> "error " + error + " user=" + user;
        };
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
}


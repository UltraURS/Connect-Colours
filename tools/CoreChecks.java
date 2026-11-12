import io.github.connectcolours.usage.DailyUsage;
import java.time.Instant;
import java.time.ZoneId;

public final class CoreChecks {
    private static int checks;

    private static void equal(Object expected, Object actual) {
        checks++;
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    private static void day(String now, String zone, String expected) {
        equal(Instant.parse(expected).toEpochMilli(), DailyUsage.startOfDay(
                Instant.parse(now).toEpochMilli(), ZoneId.of(zone)));
    }

    public static void main(String[] args) {
        day("2026-10-20T01:30:00Z", "Asia/Hong_Kong", "2026-10-19T16:00:00Z");
        day("2026-10-19T15:59:59Z", "Asia/Hong_Kong", "2026-10-18T16:00:00Z");
        day("2026-10-19T16:00:00Z", "Asia/Hong_Kong", "2026-10-19T16:00:00Z");
        day("2026-01-01T01:00:00Z", "Asia/Hong_Kong", "2025-12-31T16:00:00Z");
        day("2026-03-08T20:00:00Z", "America/New_York", "2026-03-08T05:00:00Z");
        day("2026-11-01T20:00:00Z", "America/New_York", "2026-11-01T04:00:00Z");
        equal("今日 0 B", DailyUsage.label(0));
        equal("今日 1023 B", DailyUsage.label(1023));
        equal("今日 1.0 KB", DailyUsage.label(1024));
        equal("今日 1.0 MB", DailyUsage.label(1024L * 1024));
        equal("今日 1.00 GB", DailyUsage.label(1024L * 1024 * 1024));
        equal("今日 5.00 GB", DailyUsage.label(5L * 1024 * 1024 * 1024));
        equal("今日 1.50 GB", DailyUsage.label(1536L * 1024 * 1024));
        try {
            DailyUsage.label(-1);
            throw new AssertionError("Negative usage was accepted");
        } catch (IllegalArgumentException expected) {
            checks++;
        }
        System.out.println(checks + " core checks passed.");
    }
}

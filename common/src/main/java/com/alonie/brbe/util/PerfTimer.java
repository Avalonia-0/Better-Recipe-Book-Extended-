package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Minimal per-section timer for diagnosing recipe-book opening lag.
 * Active only when config is available; falls back to no-op otherwise.
 *
 * Usage in updateCollections:
 * <pre>
 *   PerfTimer.begin();
 *   PerfTimer.start("section");
 *   // ... work ...
 *   PerfTimer.end("section");
 *   PerfTimer.logAndReset("updateCollections");
 * </pre>
 */
public final class PerfTimer {
    private static final Logger LOGGER = LogManager.getLogger("BRBE-Perf");
    private static final Object2LongOpenHashMap<String> accumNanos = new Object2LongOpenHashMap<>();
    private static final Object2LongOpenHashMap<String> counts = new Object2LongOpenHashMap<>();
    private static final ThreadLocal<Object2LongOpenHashMap<String>> active =
            ThreadLocal.withInitial(Object2LongOpenHashMap::new);
    private static boolean enabled;

    /** Set by pipeline pageUpdate to trigger render-frame timing log. */
    public static boolean logNextRenderFrame;

    private PerfTimer() {}

    /** Call at the top of updateCollections to arm. */
    public static void begin() {
        enabled = BetterRecipeBook.config != null;
        if (!enabled) return;
        active.get().clear();
    }

    /** Start a named section. Nesting not supported. */
    public static void start(String section) {
        if (!enabled) return;
        active.get().put(section, System.nanoTime());
    }

    /** End a named section and accumulate. */
    public static void end(String section) {
        if (!enabled) return;
        long elapsed = System.nanoTime() - active.get().getLong(section);
        accumNanos.addTo(section, elapsed);
        counts.addTo(section, 1);
    }

    /** Print accumulated times and reset counters. */
    public static void logAndReset(String context) {
        if (!enabled || accumNanos.isEmpty()) return;
        long total = 0;
        for (var entry : accumNanos.object2LongEntrySet()) total += entry.getLongValue();

        LOGGER.info("--- BRBE-Perf [{}] total={}ms ---", context, total / 1_000_000);
        accumNanos.object2LongEntrySet().stream()
                .sorted((a, b) -> Long.compare(b.getLongValue(), a.getLongValue()))
                .forEach(e -> {
                    String section = e.getKey();
                    long nanos = e.getLongValue();
                    long cnt = counts.getLong(section);
                    long avg = cnt > 0 ? nanos / cnt : nanos;
                    LOGGER.info("  {} : {}ms ({}x, avg {}µs)",
                            section, nanos / 1_000_000, cnt, avg / 1_000);
                });
        accumNanos.clear();
        counts.clear();
        enabled = false;
    }
}

package com.alonie.brbe.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Lightweight debug logger for tracing config-change-to-UI-refresh paths.
 *
 * <h3>Activation</h3>
 * Controlled by the JVM system property {@code brbe.debug}.  When set to
 * {@code true} (e.g. via {@code -Dbrbe.debug=true}), debug output is written
 * to {@code <gameDir>/logs/brbe-debug.log}.  When absent or any other value,
 * all logging calls are no-ops — zero runtime overhead after JIT.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   BrbeLogger.log(BrbeLogger.CONFIG, "partialCraftingEnabled: %s -> %s", old, now);
 * }</pre>
 *
 * <h3>Output format</h3>
 * {@code [HH:MM:SS.mmm] [CATEGORY] message}
 */
public final class BrbeLogger {

    /** JVM property name: {@code brbe.debug}.  Set to {@code true} to enable. */
    public static final String PROPERTY = "zzzbrbe.debug";

    /** Evaluated once at class load — never changes during the session. */
    private static final boolean ENABLED =
            "true".equalsIgnoreCase(System.getProperty(PROPERTY));

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static volatile PrintWriter writer;

    private BrbeLogger() {}

    // ── Public API ─────────────────────────────────────────────────

    /** Whether debug logging is active for this session. */
    public static boolean isEnabled() {
        return ENABLED;
    }

    /**
     * Initialise the log file.  Must be called once during mod init with
     * the game directory.  If the property is not set, this is a no-op.
     */
    public static void init(Path gameDir) {
        if (!ENABLED || writer != null) return;

        Path logsDir = gameDir.resolve("logs");
        try {
            Files.createDirectories(logsDir);
            writer = new PrintWriter(
                    Files.newBufferedWriter(
                            logsDir.resolve("zzzbrbe-debug.log"),
                            StandardCharsets.UTF_8),
                    true /* autoFlush */);
            writer.println("=== BRBE Debug Log ===");
            writer.println("Session: " + java.time.Instant.now());
            writer.println();
        } catch (IOException e) {
            System.err.println("[BrbeLogger] Failed to create log file: " + e);
        }
    }

    /**
     * Write a categorised log line.  When disabled, this is a no-op
     * (and HotSpot will inline it away entirely).
     */
    public static void log(Category cat, String format, Object... args) {
        if (!ENABLED || writer == null) return;

        String msg = args.length == 0 ? format : String.format(format, args);
        writer.printf("[%s] [%s] %s%n", TIME_FMT.format(LocalTime.now()), cat, msg);
    }

    /**
     * Write a categorised log line with an exception stack trace.
     */
    public static void log(Category cat, String msg, Throwable t) {
        if (!ENABLED || writer == null) return;

        writer.printf("[%s] [%s] %s%n", TIME_FMT.format(LocalTime.now()), cat, msg);
        t.printStackTrace(writer);
    }

    // ── Categories ─────────────────────────────────────────────────

    public enum Category {
        /** Config save/load events, field value transitions */
        CONFIG,
        /** Collection pipeline stage entry/exit */
        PIPELINE,
        /** Sort operations (pin sort, partial sort) */
        SORT,
        /** Filter toggle state changes */
        FILTER,
        /** Render frame events (configChanged flag consumption) */
        RENDER,
        /** Recipe book internal state (initVisuals, updateCollections) */
        STATE,
        /** Visibility transitions (setVisible, toggleVisibility) */
        VISIBILITY
    }
}

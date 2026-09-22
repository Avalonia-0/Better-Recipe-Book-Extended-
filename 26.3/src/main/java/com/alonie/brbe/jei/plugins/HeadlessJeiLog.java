package com.alonie.brbe.jei.plugins;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 无头 JEI 的日志总闸门 —— 与 BRBE 主 mod **共用同一个 JVM 参数** {@code -Dbrbe.debug=true}。
 *
 * <p>无头 JEI 是独立 mod（id {@code zheadlessjei}），但它的日志分两类，处理方式不同：</p>
 * <ol>
 *   <li><b>{@code com.alonie.brbe.jei.*}（本项目自有的无头核心/收集器）</b>：调用
 *       {@link #log(String, String, Object...)}，与 BRBE 主 mod 一模一样的行为——不开开关时
 *       空操作，开了才写文件。</li>
 *   <li><b>{@code mezz.jei.*}（官方 JEI 源码，逐字节上游）</b>：<b>不改源码</b>，改为在
 *       {@link #init(Path)} 里按开关设置 log4j 级别——关闭时把 {@code mezz.jei} 抬到
 *       {@code WARN}（那些 "Starting JEI… / took 214.2 microseconds" 之类的 INFO 就不再进
 *       {@code latest.log}），开启时恢复 {@code INFO}。</li>
 * </ol>
 *
 * <p>输出与 BRBE 主 mod <b>同一个文件</b> {@code <gameDir>/logs/brbe-debug.log}
 * （以追加方式打开，两个 mod 各写各的行，不会互相截断）。</p>
 */
public final class HeadlessJeiLog {

    /** 与 BRBE 主 mod 共用的 JVM 属性名。 */
    public static final String PROPERTY = "brbe.debug";

    private static final boolean ENABLED = "true".equalsIgnoreCase(System.getProperty(PROPERTY));
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static volatile PrintWriter writer;
    private static boolean jeiLevelApplied;

    private HeadlessJeiLog() {
    }

    /** 本次会话是否开启了调试日志（与 BRBE 主 mod 同源）。 */
    public static boolean isEnabled() {
        return ENABLED;
    }

    /**
     * 客户端入口调用一次：开启时打开日志文件（追加），并统一官方 JEI 的日志级别。
     *
     * @param gameDir 游戏目录
     */
    public static void init(Path gameDir) {
        if (!ENABLED) {
            applyJeiLoggerLevel(false);
            return;
        }
        applyJeiLoggerLevel(true);
        if (writer != null) return;
        Path logsDir = gameDir.resolve("logs");
        try {
            Files.createDirectories(logsDir);
            // 一律追加（BRBE 主 mod 也用追加 + 会话头 + 4MB 上限），谁先谁后都不丢内容。
            writer = new PrintWriter(Files.newBufferedWriter(
                    logsDir.resolve("brbe-debug.log"), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND), true);
            writer.println("--- headless-jei attached (" + java.time.Instant.now() + ") ---");
        } catch (IOException e) {
            System.err.println("[headless-jei] 无法打开调试日志文件: " + e);
        }
    }

    /** 写一行调试日志；未启用时空操作。格式串用 {@code {}} 顺序占位（与 BRBE 一致）。 */
    public static void log(String tag, String format, Object... args) {
        if (!ENABLED || writer == null) return;
        writer.printf("[%s] [%s] %s%n", TIME_FMT.format(LocalTime.now()), tag, fill(format, args));
    }

    /** 写一行调试日志 + 异常堆栈；未启用时空操作。 */
    public static void log(String tag, String message, Throwable t) {
        if (!ENABLED || writer == null) return;
        writer.printf("[%s] [%s] %s%n", TIME_FMT.format(LocalTime.now()), tag, message);
        t.printStackTrace(writer);
    }

    private static String fill(String format, Object... args) {
        if (format == null || args.length == 0) return format;
        StringBuilder sb = new StringBuilder(format.length() + 32);
        int arg = 0;
        int from = 0;
        while (from < format.length()) {
            int at = format.indexOf("{}", from);
            if (at < 0 || arg >= args.length) {
                sb.append(format, from, format.length());
                break;
            }
            sb.append(format, from, at).append(args[arg++]);
            from = at + 2;
        }
        return sb.toString();
    }

    /**
     * 按开关调整官方 JEI（{@code mezz.jei.*}）的 log4j 级别：关闭时 WARN，开启时 INFO。
     *
     * <p>用反射调 {@code org.apache.logging.log4j.core.config.Configurator.setLevel}，
     * 避免给本工程新增 log4j-core 编译依赖；失败只记一次 stderr，不影响游戏。</p>
     */
    private static void applyJeiLoggerLevel(boolean verbose) {
        if (jeiLevelApplied) return;
        jeiLevelApplied = true;
        try {
            Class<?> levelClass = Class.forName("org.apache.logging.log4j.Level");
            Object level = levelClass.getField(verbose ? "INFO" : "WARN").get(null);
            Class<?> configurator = Class.forName("org.apache.logging.log4j.core.config.Configurator");
            configurator.getMethod("setLevel", String.class, levelClass)
                    .invoke(null, "mezz.jei", level);
        } catch (Throwable t) {
            System.err.println("[headless-jei] 调整 mezz.jei 日志级别失败: " + t);
        }
    }
}

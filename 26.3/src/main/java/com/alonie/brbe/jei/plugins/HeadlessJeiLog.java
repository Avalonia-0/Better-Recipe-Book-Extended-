package com.alonie.brbe.jei.plugins;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.ThresholdFilter;

/**
 * 无头 JEI 的日志出口 —— 与 BRBE 主 mod <b>同一个文件</b>
 * {@code <gameDir>/logs/brbe-debug.log}。
 *
 * <h3>没有开关</h3>
 * 本 mod 与 BRBE 的调试行<b>恒写</b>该文件，不再有 JVM 参数门控；{@code latest.log}
 * 保持干净。文件恒以 {@code CREATE+APPEND}（O_APPEND）打开 —— 与 BRBE 主 mod 的
 * 句柄并列写入，任何一方都不能用非追加句柄（见根仓库
 * {@code docs/brbe-debug-log-写入冲突诊断.md}）。
 *
 * <h3>官方 {@code mezz.jei.*} 的日志</h3>
 * {@link #init(Path, boolean)} 在 {@code routeJeiLogs=true} 时把 log4j 的
 * {@code mezz.jei} logger 接到本文件：
 * <ul>
 *   <li>{@code additivity=false} + 本文件 appender → JEI 的
 *       {@code Starting JEI… / took 214.2 microseconds / Registering recipes…}
 *       这些 INFO 不再刷 {@code latest.log}；</li>
 *   <li>WARN 及以上<b>旁路</b>回原 {@code File} appender（threshold=WARN）→
 *       JEI 的告警/错误仍然出现在 {@code latest.log} 里，不会因为"日志搬家"而丢失。</li>
 * </ul>
 * <b>只在没有真实 JEI 时调用</b>（无头核心自任 JEI 时才接管它的日志；玩家自己装了
 * 真实 JEI 时那是它自己的日志，不劫持）。
 *
 * <p>appender 不是 {@code FileAppender} 而是自有实现：一是 log4j 的
 * {@code FileAppender.Builder} 在 2.22/2.25（{@code withFileName}）与 2.26
 * （{@code setFileName}）之间改过名，自有 appender 走 {@code AbstractAppender} 的
 * 4 参构造器（三版本一致）；二是这样 log4j 的行与自有行共用同一个 {@code PrintWriter}
 * （同一 O_APPEND 句柄），少一个文件句柄、也杜绝了"appender 以 append=false 打开把
 * BRBE 会话头截掉"这类事故。</p>
 */
public final class HeadlessJeiLog {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /** 承载 log4j 转发行的 appender 名（重复 init 时用来判断是否已挂过）。 */
    private static final String APPENDER_NAME = "brbe-debug-file";

    private static volatile PrintWriter writer;
    private static boolean routesApplied;

    private HeadlessJeiLog() {
    }

    /**
     * 客户端入口调用一次：打开日志文件（追加），并在需要时把官方 JEI 的日志路由进来。
     *
     * @param gameDir      游戏目录
     * @param routeJeiLogs 是否接管 {@code mezz.jei} 日志（= 没有真实 JEI，无头核心在跑）
     */
    public static void init(Path gameDir, boolean routeJeiLogs) {
        if (writer == null) {
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
        if (routeJeiLogs && !routesApplied && writer != null) {
            routesApplied = true;
            routeJeiLogs();
        }
    }

    /** 写一行调试日志。格式串用 {@code {}} 顺序占位（与 BRBE 一致）。 */
    public static void log(String tag, String format, Object... args) {
        writeLine("[" + TIME_FMT.format(LocalTime.now()) + "] [" + tag + "] " + fill(format, args));
    }

    /** 写一行调试日志 + 异常堆栈。 */
    public static void log(String tag, String message, Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        writeLine("[" + TIME_FMT.format(LocalTime.now()) + "] [" + tag + "] " + message
                + System.lineSeparator() + sw);
    }

    /** 单点写入（自有行与 log4j 转发行共用），避免两条线程交错写半行。 */
    private static void writeLine(String line) {
        PrintWriter out = writer;
        if (out == null) return;
        synchronized (HeadlessJeiLog.class) {
            out.println(line);
        }
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
        if (arg < args.length) {
            for (int i = arg; i < args.length; i++) {
                sb.append(' ').append(args[i]);
            }
        }
        return sb.toString();
    }

    /**
     * 把 {@code mezz.jei} 的 log4j 输出接到 {@code brbe-debug.log}（INFO 起），
     * WARN 及以上旁路回原 {@code File} appender（latest.log）。
     *
     * <p>log4j-core 是 Minecraft 自带的运行时依赖，这里按编译期类型使用（版本随各分支的
     * MC 版本，不存在跨版本 API 漂移——唯一有漂移的两个方法名已用双名反射兜住）。</p>
     */
    private static void routeJeiLogs() {
        try {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            Configuration config = context.getConfiguration();
            if (config.getAppender(APPENDER_NAME) != null) return;   // 已经挂过

            // getLoggerConfig 在没有专门配置时返回**根**配置——直接改它会掐掉全部日志，
            // 所以只在确实命中 mezz.jei 时复用，否则新建一个（构造器即 additivity=false）。
            LoggerConfig loggerConfig = config.getLoggerConfig("mezz.jei");
            if (!"mezz.jei".equals(loggerConfig.getName())) {
                loggerConfig = new LoggerConfig("mezz.jei", Level.INFO, false);
                config.addLogger("mezz.jei", loggerConfig);
            } else {
                loggerConfig.setLevel(Level.INFO);
                setAdditivity(loggerConfig, false);
            }

            JeiAppender appender = new JeiAppender();
            appender.start();
            config.addAppender(appender);
            loggerConfig.addAppender(appender, Level.INFO, null);

            Appender latestLog = config.getAppender("File");
            if (latestLog != null) {
                loggerConfig.addAppender(latestLog, Level.WARN, ThresholdFilter.createFilter(
                        Level.WARN, Filter.Result.NEUTRAL, Filter.Result.DENY));
            }
            context.updateLoggers();
            log("BRBE-JEI-PLUGINS",
                    "mezz.jei logs routed here (INFO+); WARN+ also kept in latest.log: {}",
                    latestLog != null);
        } catch (Throwable t) {
            // 路由失败不影响游戏：JEI 的日志仍在 latest.log（与旧行为一致）
            System.err.println("[headless-jei] 路由 mezz.jei 日志失败: " + t);
        }
    }

    /** log4j 2.26 叫 {@code setAdditive}，2.17/2.25 叫 {@code setAdditivity}——两个名字都试。 */
    private static void setAdditivity(LoggerConfig loggerConfig, boolean additive) {
        for (String name : new String[] {"setAdditive", "setAdditivity"}) {
            try {
                loggerConfig.getClass().getMethod(name, boolean.class).invoke(loggerConfig, additive);
                return;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // 试下一个名字
            }
        }
    }

    /** 把 log4j 事件写进本文件的 appender（自有实现，见类注释）。 */
    private static final class JeiAppender extends AbstractAppender {

        JeiAppender() {
            super(APPENDER_NAME, null, null, true);   // layout=null：下面自己格式化
        }

        @Override
        public void append(LogEvent event) {
            StringBuilder line = new StringBuilder(160);
            line.append('[').append(TIME_FMT.format(LocalTime.now()))
                    .append("] [").append(event.getThreadName())
                    .append('/').append(event.getLevel()).append("] [")
                    .append(shortLoggerName(event.getLoggerName())).append("] ")
                    .append(event.getMessage().getFormattedMessage());
            Throwable thrown = event.getThrown();
            if (thrown != null) {
                StringWriter sw = new StringWriter();
                thrown.printStackTrace(new PrintWriter(sw));
                line.append(System.lineSeparator()).append(sw);
            }
            writeLine(line.toString());
        }

        /** {@code mezz.jei.library.foo.Bar} → {@code j.l.foo.Bar}（与 log4j 的 %logger{1} 同义）。 */
        private static String shortLoggerName(String name) {
            if (name == null) return "?";
            String[] parts = name.split("\\.");
            if (parts.length <= 2) return name;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length - 2; i++) {
                sb.append(parts[i].charAt(0)).append('.');
            }
            sb.append(parts[parts.length - 2]).append('.').append(parts[parts.length - 1]);
            return sb.toString();
        }
    }
}

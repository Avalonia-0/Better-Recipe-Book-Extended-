package com.alonie.brbe.util;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * BRBE 的日志出口 —— <b>恒写文件，没有开关</b>。
 *
 * <h3>输出位置</h3>
 * 所有调试输出写入 {@code <gameDir>/logs/brbe-debug.log}，<b>不再污染
 * {@code latest.log}</b>。无头 JEI（内嵌的独立 mod）与官方 {@code mezz.jei} 的日志
 * （由它路由）也在这同一个文件里，所以任何一次会话的完整轨迹都在一个文件里。
 *
 * <h3>为什么单独一个文件</h3>
 * 这些行（缓存统计、索引重建、管线阶段…）数量大、混在 {@code latest.log} 里既刷屏
 * 又拖慢磁盘写入；独立文件还让"用户没开日志开关 → 排查时没有日志"这类来回彻底消失。
 *
 * <h3>用法</h3>
 * <pre>{@code
 *   BrbeLogger.log("BRBE-CACHE", "injected {} cached, {} skipped", a, b);   // {} 顺序替换
 *   BrbeLogger.log("BRBE-DIAG", "failed: " + e, e);                        // 带堆栈
 *   if (BrbeLogger.diagnosticsEnabled()) { ...每次刷新都做重活的开发期自检... }
 * }</pre>
 *
 * <h3>输出格式</h3>
 * {@code [HH:mm:ss.SSS] [TAG] message}
 *
 * <p><b>注意</b>：真正的故障（配置/存档文件读写失败、工作站配置项非法、mixins/兼容注册
 * 失败…）继续走 {@code LOGGER.warn/error}，用户默认就能在 {@code latest.log} 看到。</p>
 *
 * <p><b>开发期自检闸门</b>：少数诊断每次刷新都要做 O(n) 重活（配方状态独立预测、
 * 集合计数…），它们<b>不</b>跟着日志走，而是由 {@code -Dbrbe.diag=true}
 * （{@link #diagnosticsEnabled()}）单独控制，默认关。</p>
 */
public final class BrbeLogger {

    /** 开发期自检闸门：{@code -Dbrbe.diag=true}。与日志无关（日志恒写）。 */
    public static final String DIAG_PROPERTY = "brbe.diag";

    /** 类加载时求值一次，会话内不变。 */
    private static final boolean DIAGNOSTICS =
            "true".equalsIgnoreCase(System.getProperty(DIAG_PROPERTY));

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static volatile PrintWriter writer;

    private BrbeLogger() {
    }

    /**
     * 开发期自检是否开启（{@code -Dbrbe.diag=true}，默认关）。
     *
     * <p>用于"每次物品栏刷新都要对全部配方做一遍独立预测"这类<b>昂贵</b>的诊断
     * （{@code RecipeStateDiagnostic} 等）——它们借日志文件输出，但绝不能常开。</p>
     */
    public static boolean diagnosticsEnabled() {
        return DIAGNOSTICS;
    }

    /**
     * 初始化日志文件（每次客户端启动调用一次，带 gameDir）。
     *
     * @param gameDir 游戏目录，日志写到 {@code <gameDir>/logs/brbe-debug.log}
     */
    public static void init(Path gameDir) {
        if (writer != null) return;
        Path logsDir = gameDir.resolve("logs");
        try {
            Files.createDirectories(logsDir);
            Path file = logsDir.resolve("brbe-debug.log");
            // ⚠️ 必须**始终**以 APPEND 打开：无头 JEI（独立 mod：26.x 内嵌、1.21.1 另装）也写这个文件，
            // 它的句柄是 O_APPEND（写到文件真实末尾），而**非追加**句柄的写入走自己的
            // 文件位置——两个句柄并存时，后者的每次写入都会把对方追加在末尾的字节整段
            // 盖掉（实测：A 非追加写 2 行，B 追加的整行消失）。
            // 文件过大时（>4MB）才重新开始：NIO 不允许 APPEND + TRUNCATE_EXISTING 同时
            // 使用（IllegalArgumentException），所以先删除再以 APPEND 打开。
            boolean fresh = !Files.exists(file) || Files.size(file) > 4L * 1024 * 1024;
            if (fresh) {
                // 就地清空（不是删除再建）：其他写者（无头 JEI）的 O_APPEND 句柄仍指向同一
                // inode，清空后继续追加到新末尾；删除再建会让它们的句柄悬在已 unlink 的
                // inode 上，之后的输出全部写进"看不见的文件"。
                try (FileChannel ignored = FileChannel.open(file,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING)) {
                    // 只为清空
                }
            }
            writer = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND), true /* autoFlush */);
            writer.println("=== BRBE Debug Log ===");
            writer.println("Session: " + java.time.Instant.now());
            writer.println();
        } catch (IOException e) {
            System.err.println("[BrbeLogger] 无法创建调试日志文件: " + e);
        }
    }

    /** 写一行调试日志。格式串用 {@code {}} 占位（与全仓库既有日志一致）。 */
    public static void log(String tag, String format, Object... args) {
        PrintWriter out = writer;
        if (out == null) return;
        out.printf("[%s] [%s] %s%n", TIME_FMT.format(LocalTime.now()), tag, fill(format, args));
    }

    /** {@code {}} 顺序替换（log4j 风格，不做 % 转义，含中文/花括号的文案安全）。 */
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

    /** 写一行调试日志 + 异常堆栈。 */
    public static void log(String tag, String message, Throwable t) {
        PrintWriter out = writer;
        if (out == null) return;
        out.printf("[%s] [%s] %s%n", TIME_FMT.format(LocalTime.now()), tag, message);
        t.printStackTrace(out);
    }
}

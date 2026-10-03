package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the <b>save / server scope</b> a store should live in, and notifies
 * the stores when that scope changes.
 *
 * <p>Not every BRBE store is global: recipe pins ({@code brbe.pins.json}) and
 * RBIP tab pins ({@code brbe.tabpins.json}) stay in the client game directory,
 * but the pinned query windows, the query window placement and the recipe book
 * browsing positions belong to the world they were made in.  Those stores ask
 * this class for a directory instead of {@code Minecraft.gameDirectory}:</p>
 *
 * <ul>
 *   <li><b>single player</b> — {@code <world>/brbe/} (the world folder of the
 *       integrated server, so the data travels with the save);</li>
 *   <li><b>multiplayer</b> — {@code <gameDir>/brbe/servers/<key>/}, where
 *       {@code <key>} is the sanitized server address (Realms use the realm
 *       name, LAN entries drop the per-session port so their key stays
 *       stable);</li>
 *   <li><b>no world</b> (main menu, between worlds) — {@code null}, which every
 *       store must read as "do not read, do not write".</li>
 * </ul>
 *
 * <p>{@link #refresh()} is safe to call every frame: while the scope token is
 * unchanged it costs two getters plus one reference comparison, and the
 * filesystem path is only resolved when the token actually changes.</p>
 *
 * <p>作用域切换时监听者会收到新目录：它应当先把旧作用域的数据落盘（文件路径由
 * 各存储自己记着），再清空内存、载入新作用域——否则退出世界后内存里的 pin /
 * 浏览位置会泄漏到下一个存档或服务器。</p>
 */
public final class WorldScopedStore {

    /** 作用域变化回调；{@code dir} 为 null 表示当前无作用域（不读不写）。 */
    public interface Listener {
        void onScopeChanged(Path dir);
    }

    /** 作用域目录名（存档内与 {@code gameDir} 内同名）。 */
    private static final String DIR_NAME = "brbe";
    /** 多人服务器的子目录名。 */
    private static final String SERVER_DIR_NAME = "servers";

    private static final List<Listener> listeners = new ArrayList<>();

    /** 作用域标识：单机 = {@link IntegratedServer} 实例，多人 = {@link ServerData}
     *  实例，null = 无世界。用实例身份而非路径比较，避免每帧解析路径。 */
    private static Object token;
    private static Path dir;
    private static boolean resolved;

    private WorldScopedStore() {}

    /** 注册作用域监听者；已解析过作用域时立即同步一次当前值。 */
    public static void addListener(Listener listener) {
        if (listener == null || listeners.contains(listener)) return;
        listeners.add(listener);
        if (resolved) notifyOne(listener, dir);
    }

    /** 当前作用域目录；无作用域返回 null。 */
    public static Path dir() {
        return dir;
    }

    /**
     * 当前作用域的稳定散列（同一存档 / 同一服务器恒定，作用域一变就变）——
     * 供"每个存档稳定"的伪随机使用（如数据包标签的图标：同一存档内不跳动、换世界才可能换一个）。
     * 无作用域时为 0。
     */
    public static int salt() {
        Path d = dir;
        return d == null ? 0 : d.toString().hashCode();
    }

    /**
     * 当前是不是**联机到真正的服务器**（既不是单机，也不是自己开局的局域网主机）。
     *
     * <p>判定与作用域 token 同源：{@code mc.level} 存在时，集成服务器在场 = 本地世界
     * （单机 / 局域网主机，服务端资源包列表在本进程内可读）；否则就是远端 {@link ServerData}。
     * 主菜单 / 未进世界返回 false。</p>
     *
     * <p>用途：RBIP 的「数据包」标签档需要**服务端的资源包列表**（{@code RecipePackIndex}），
     * 联机时网络协议里没有这条信息 —— 该档在联机下按「命名空间」档跑，配置界面也把它藏起来，
     * 离开服务器自动恢复（用户 2026-10-01 定）。</p>
     */
    public static boolean onRemoteServer() {
        return tokenFor(Minecraft.getInstance()) instanceof ServerData;
    }

    /** 当前作用域内的文件路径；无作用域返回 null（调用方据此跳过读写）。 */
    public static Path file(String name) {
        Path d = dir;
        return d == null || name == null ? null : d.resolve(name);
    }

    /** 客户端 gameDir 下的路径（旧版全局文件位置，迁移用）。 */
    public static Path gameDirFile(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameDirectory == null || name == null) return null;
        return mc.gameDirectory.toPath().resolve(name);
    }

    /** 重新解析作用域，变化时依次通知监听者。可每帧调用。 */
    public static void refresh() {
        Minecraft mc = Minecraft.getInstance();
        Object next = tokenFor(mc);
        if (resolved && next == token) return;
        token = next;
        resolved = true;
        Path nextDir = next == null ? null : resolveDir(mc, next);
        if (nextDir != null && nextDir.equals(dir)) return;
        dir = nextDir;
        for (Listener listener : new ArrayList<>(listeners)) {
            notifyOne(listener, nextDir);
        }
    }

    /**
     * 一次性迁移：把旧版放在 gameDir 的全局文件搬进作用域。
     *
     * <p>只在作用域文件不存在、旧文件存在时执行；复制成功后把旧文件改名为
     * {@code <name>.migrated}（数据保留、不再被读取——否则每个新作用域都会继承
     * 同一份旧数据，玩家会看到"别的世界的 pin 到处冒出来"）。</p>
     */
    public static void migrateLegacy(String legacyName, Path scopedFile) {
        if (legacyName == null || scopedFile == null) return;
        try {
            if (Files.exists(scopedFile)) return;
            Path legacy = gameDirFile(legacyName);
            if (legacy == null || !Files.exists(legacy)) return;
            Files.createDirectories(scopedFile.getParent());
            Files.copy(legacy, scopedFile);
            Path archived = legacy.resolveSibling(legacyName + ".migrated");
            if (Files.exists(archived)) {
                archived = legacy.resolveSibling(legacyName + ".migrated-" + System.currentTimeMillis());
            }
            Files.move(legacy, archived);
            BetterRecipeBook.LOGGER.info("[BRBE] Migrated global {} into {} (old file kept as {})",
                    legacyName, scopedFile, archived.getFileName());
        } catch (Exception e) {
            BetterRecipeBook.LOGGER.warn("[BRBE] Failed to migrate {}: {}", legacyName, e.getMessage());
        }
    }

    private static void notifyOne(Listener listener, Path scopeDir) {
        try {
            listener.onScopeChanged(scopeDir);
        } catch (Exception e) {
            BetterRecipeBook.LOGGER.warn("[BRBE] Scope change handler failed: {}", e.getMessage());
        }
    }

    private static Object tokenFor(Minecraft mc) {
        if (mc == null || mc.level == null) return null;
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) return server;
        return mc.getCurrentServer();
    }

    private static Path resolveDir(Minecraft mc, Object scopeToken) {
        try {
            if (scopeToken instanceof IntegratedServer server) {
                return server.getWorldPath(LevelResource.ROOT).resolve(DIR_NAME);
            }
            if (scopeToken instanceof ServerData data) {
                if (mc.gameDirectory == null) return null;
                String key = serverKey(data);
                if (key.isEmpty()) return null;
                return mc.gameDirectory.toPath()
                        .resolve(DIR_NAME)
                        .resolve(SERVER_DIR_NAME)
                        .resolve(key);
            }
        } catch (Exception e) {
            BetterRecipeBook.LOGGER.warn("[BRBE] Failed to resolve world scope: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 服务器作用域目录名：地址优先（唯一），Realms 用名字，LAN 去掉每次会话都会
     * 变化的端口，最后都退化为服务器名；路径非法字符（{@code / \ : * ? " < > |}
     * 与控制字符）替换成 {@code _}，去掉结尾的点（Windows 不允许），过长则截断加
     * 哈希；无法确定时返回空串（调用方视为"无作用域"，宁可不存也不写到会撞车的目录）。
     */
    public static String serverKey(ServerData data) {
        String key;
        if (data.isRealm()) {
            key = sanitize(data.name);
            // Realms 的 ip 不可用；名字也取不到时才算无法确定作用域
            key = key.isEmpty() ? sanitize(data.ip) : "realm-" + key;
        } else if (data.isLan()) {
            key = sanitize(stripPort(data.ip));
            if (key.isEmpty()) key = sanitize(data.name);
        } else {
            key = sanitize(data.ip);
            if (key.isEmpty()) key = sanitize(data.name);
        }
        return key;
    }

    private static String stripPort(String address) {
        if (address == null) return "";
        int close = address.lastIndexOf(']');
        // IPv6 字面量形如 [::1]:25565 —— 去掉 ']' 之后的部分
        if (close >= 0) return address.substring(0, close + 1);
        int colon = address.lastIndexOf(':');
        if (colon > 0) {
            String tail = address.substring(colon + 1);
            if (!tail.isEmpty() && tail.chars().allMatch(Character::isDigit)) {
                return address.substring(0, colon);
            }
        }
        return address;
    }

    private static String sanitize(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean hostile = c < ' ' || c == 0x7f
                    || c == '/' || c == '\\' || c == ':' || c == '*'
                    || c == '?' || c == '"' || c == '<' || c == '>' || c == '|';
            sb.append(hostile ? '_' : c);
        }
        String out = sb.toString().trim();
        while (out.endsWith(".")) {
            out = out.substring(0, out.length() - 1);
        }
        if (out.length() > 120) {
            out = out.substring(0, 100) + "-" + Integer.toHexString(out.hashCode());
        }
        return out;
    }
}

package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 「进度系统已解锁」的配方 display 白名单 —— {@code unlockAll} 关闭时配方书的显示依据。
 *
 * <h3>为什么需要它（2026-09-25）</h3>
 * 关掉「自动解锁所有配方」后，BRBE 以前只做一件事：撤销<b>自己注入</b>的 display。
 * 这在纯净环境等价于"恢复服务端状态"，但只要装了任何"解锁全部"的模组（如整合包里的
 * {@code get-recipes}，它给服务端配方书授予全部配方），服务端状态本身就是全解锁 ——
 * 于是开关关掉后配方书照样全亮，玩家看到的是"进度系统里根本没解锁的配方没有被隐藏"。
 *
 * <p>配方书里"哪些是进度解锁的"无法从服务端配方书反推（模组授予与进度授予在那里
 * 长得一模一样），唯一权威信号是<b>原版进度系统</b>：每条原版配方都有一条
 * {@code minecraft:advancement/recipes/**} 成就（26.2 共 1572 条），其
 * {@code rewards.recipes} 就是该成就解锁的配方；BRBE 自己生成的
 * {@code brbe:recipe/**}（模组锻造）同样走 {@code rewards.recipes}。因此：
 *
 * <pre>白名单 = ⋃ { advancement.rewards.recipes() | 该 advancement 已完成 }</pre>
 *
 * <p>与酿造/锻造书既有的 {@code RecipeUnlockTracker} 同一个进度权威，语义一致：
 * 开 {@code unlockAll} = 全量；关 = 只显示进度解锁的。</p>
 *
 * <h3>可用性</h3>
 * 需要集成服务器（单机）才能枚举"玩家完成了哪些成就"和"配方 → display"。多人服务器上
 * 无法判定，此时 {@link #whitelist()} 返回 {@code null}，调用方<b>不过滤</b>（保持
 * 服务端状态；与 {@code unlockAll} 注入本身只支持单机一致）。判定结果按"脏标记"缓存，
 * 只在世界加入 / 成就变化 / 开关变化后重算一次。
 */
public final class ProgressionUnlocks {

    /** 白名单；{@code null} = 不可判定（多人/无服务器）或尚未算出。 */
    private static Set<RecipeDisplayId> whitelist;
    /** 上次算出白名单所用的配方集合——display 枚举昂贵，只有它变了才重算。 */
    private static Set<ResourceKey<Recipe<?>>> lastRecipes = Set.of();
    private static boolean computed;
    private static boolean dirty = true;
    private static long lastCheckMillis;
    /** 廉价重算（服务器枚举已完成成就）的最小间隔：成就包可能一秒来好几次。 */
    private static final long MIN_CHECK_INTERVAL_MS = 250L;
    /** 上次记录的隐藏条数（只在变化时打日志）。 */
    private static int lastHidden = -1;
    private static boolean multiPlayerNoticeLogged;

    private ProgressionUnlocks() {}

    /** 是否启用进度过滤：仅当 {@code unlockAll} 关闭（开启 = 全量显示，不过滤）。 */
    public static boolean filtersActive() {
        return BetterRecipeBook.config != null && !BetterRecipeBook.config.unlockAll;
    }

    /** 标记需要重算（成就变化 / 开关切换）。 */
    public static void markDirty() {
        dirty = true;
    }

    /** 世界卸载：清空并标记重算。 */
    public static void clear() {
        dirty = true;
        computed = false;
        whitelist = null;
        lastRecipes = Set.of();
        lastCheckMillis = 0L;
        lastHidden = -1;
        multiPlayerNoticeLogged = false;
    }

    /**
     * 进度已解锁的 display 白名单。
     *
     * @return 白名单集合；{@code null} = 无法判定（多人 / 无集成服务器）→ 调用方不过滤
     */
    public static Set<RecipeDisplayId> whitelist() {
        if (computed && !dirty) {
            return whitelist;
        }
        long now = net.minecraft.util.Util.getMillis();
        if (computed && now - lastCheckMillis < MIN_CHECK_INTERVAL_MS) {
            return whitelist;   // 节流：脏标记保留，下一轮再算
        }
        lastCheckMillis = now;
        recompute();
        computed = true;
        dirty = false;
        return whitelist;
    }

    /** 过滤方上报本轮隐藏了几条 display（仅在数量变化时记一行诊断）。 */
    public static void noteHidden(int hidden) {
        if (hidden == lastHidden) {
            return;
        }
        lastHidden = hidden;
        if (hidden > 0) {
            BrbeLogger.log("BRBE",
                    "progress filter: hid {} displays not unlocked by progression (unlockAll=false)",
                    hidden);
        }
    }

    private static void recompute() {
        whitelist = null;
        lastHidden = -1;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null
                || !minecraft.hasSingleplayerServer()) {
            if (!multiPlayerNoticeLogged) {
                multiPlayerNoticeLogged = true;
                BrbeLogger.log("BRBE",
                        "progress filter: no integrated server (multiplayer) — recipe book is not "
                                + "filtered by progression");
            }
            return;
        }
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        UUID playerId = minecraft.player.getUUID();
        Set<ResourceKey<Recipe<?>>> recipes = new HashSet<>();
        try {
            // 廉价部分：已完成成就 → rewards.recipes（一次服务器往返 + 全量 isDone 检查）。
            server.submit(() -> collectCompletedRewards(server, playerId, recipes)).join();
            if (!recipes.isEmpty() && recipes.equals(lastRecipes) && whitelist != null) {
                return;   // 配方集合没变 → 跳过昂贵的 display 枚举
            }
            // 昂贵部分：配方 key → display id（每条配方都要现构 display entry）。
            Set<RecipeDisplayId> displays = new HashSet<>();
            server.submit(() -> collectDisplays(server, recipes, displays)).join();
            lastRecipes = Set.copyOf(recipes);
            whitelist = Set.copyOf(displays);
            BrbeLogger.log("BRBE",
                    "progress filter: {} recipes / {} displays unlocked by completed advancements",
                    recipes.size(), displays.size());
        } catch (Exception e) {
            BetterRecipeBook.LOGGER.warn("[BRBE] progress filter enumeration failed: {}", e.toString());
        }
    }

    /** 服务器线程：把已完成成就的 {@code rewards.recipes} 全部收进来。 */
    private static void collectCompletedRewards(IntegratedServer server, UUID playerId,
                                                Set<ResourceKey<Recipe<?>>> out) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }
        PlayerAdvancements progress = player.getAdvancements();
        for (AdvancementHolder holder : server.getAdvancements().getAllAdvancements()) {
            try {
                if (!progress.getOrStartProgress(holder).isDone()) {
                    continue;
                }
                AdvancementRewards rewards = holder.value().rewards();
                if (rewards == null) {
                    continue;
                }
                List<ResourceKey<Recipe<?>>> granted = rewards.recipes();
                if (granted != null && !granted.isEmpty()) {
                    out.addAll(granted);
                }
            } catch (Exception | LinkageError ignored) {
                // 单条成就数据异常不影响其余
            }
        }
    }

    /** 服务器线程：配方 key → 它产生的 display id（与 unlock-all 同一套枚举）。 */
    private static void collectDisplays(IntegratedServer server, Set<ResourceKey<Recipe<?>>> recipes,
                                        Set<RecipeDisplayId> out) {
        RecipeManager manager = server.getRecipeManager();
        for (ResourceKey<Recipe<?>> key : recipes) {
            try {
                manager.listDisplaysForRecipe(key, entry -> out.add(entry.id()));
            } catch (Exception | LinkageError ignored) {
                // 配方已不存在（数据包变更）——跳过
            }
        }
    }
}

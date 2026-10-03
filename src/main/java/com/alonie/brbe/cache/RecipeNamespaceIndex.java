package com.alonie.brbe.cache;

import com.alonie.brbe.util.BrbeLogger;
import net.fabricmc.fabric.api.recipe.v1.sync.SynchronizedRecipes;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 客户端「配方注册 id 的命名空间」索引：{@link RecipeDisplay} → 产生该显示的配方 id 的命名空间。
 *
 * <p>RBIP 的「命名空间」标签模式用它把配方归到**配方自己的命名空间**（数据包 / 模组），
 * 而不是产物物品的命名空间 —— 模组加的铁粒配方进该模组标签，纯数据包配方也有自己的标签。
 * {@code RecipeDisplayEntry} 本身只有 int 型 {@code RecipeDisplayId}，网络包里没有
 * {@code ResourceKey<Recipe>}，所以 id 只能从下面三个**客户端可达**的配方来源反查；
 * display 值相等即视为同一配方（与 {@link BrbeJeiBridge} 给锻造/切石条目挂 JEI layout
 * 用的是同一套判据，生产已验证）：</p>
 *
 * <ol>
 *   <li><b>单机</b>：集成服务端的 {@code RecipeManager} —— 客户端唯一能拿到**数据包**配方的源；</li>
 *   <li><b>联机</b>：Fabric 的同步配方（服务端装了 Fabric API 才有此通道，与 JEI 同源）；</li>
 *   <li><b>兜底</b>：本地配方缓存 {@link VanillaRecipeCache}（原版 + 模组，**不含**服务端数据包；
 *       服务端不发配方时，书里的条目本来就来自它，display 天然一致）。</li>
 * </ol>
 *
 * <p>查不到（来源全部缺失 / display 对不上 / 同一 display 对应多个命名空间）时返回
 * {@code null}，调用方回退到「产物物品的命名空间」—— 即本次改动之前的行为。</p>
 *
 * <p>只索引 RBIP 会归组的配方类型（合成 + 熔炉系）；来源实例变了才重建（单机换存档、
 * 联机重新同步、缓存重新加载都会换实例），所以正常游戏里最多各重建一次。</p>
 */
public final class RecipeNamespaceIndex {

    /** 「当前没有可用来源」的哨兵身份（用于节流重试：切世界/重连期间本方法会被频繁调用）。 */
    private static final Object NO_SOURCE = new Object();
    /** 无来源时的重试间隔（毫秒）。 */
    private static final long RETRY_INTERVAL_MS = 1_000L;
    private static final Object LOCK = new Object();
    /** 给缓存条目构造 display 用的占位 id（不进索引，也不影响 display 的值相等）。 */
    private static final RecipeDisplayId CACHE_ID = new RecipeDisplayId(-1);

    private static final Map<RecipeDisplay, Identifier> BY_DISPLAY = new HashMap<>();
    /** 同一 display 对应多个命名空间的（数据包复制了一份原版配方等）→ 视为查不到。 */
    private static final Set<RecipeDisplay> AMBIGUOUS = new HashSet<>();

    /** 当前索引来源的身份；{@link #NO_SOURCE} = 尚未建成（或确实没有来源）。 */
    private static Object builtIdentity = NO_SOURCE;
    private static long nextRetryAt;

    private RecipeNamespaceIndex() {}

    /**
     * 产生该显示的**配方注册 id**（如 {@code master_cutter:stone/xxx}）；查不到返回
     * {@code null}（无来源 / display 对不上 / 同名 display 有多个命名空间）。
     *
     * <p>数据包模式拿它去 {@link RecipePackIndex} 问"这条配方来自哪个包"。</p>
     */
    public static Identifier recipeIdOf(RecipeDisplay display) {
        if (display == null) return null;
        ensureFresh();
        synchronized (LOCK) {
            if (AMBIGUOUS.contains(display)) return null;
            return BY_DISPLAY.get(display);
        }
    }

    /** 产生该**配方书条目**的配方 id：注入条目（负 id）直接问本地缓存的配方 key，
     *  其余走 display 值相等反查（注入条目的 display 是本地重建的，必然对不上）。 */
    public static Identifier recipeIdOfEntry(RecipeDisplayEntry entry) {
        if (entry == null) return null;
        try {
            RecipeDisplayId id = entry.id();
            if (id != null && id.index() < 0) {
                String key = VanillaRecipeCache.injectedRecipeKey(id.index());
                Identifier parsed = key == null ? null : Identifier.tryParse(key);
                if (parsed != null) return parsed;
            }
        } catch (Exception | LinkageError ignored) {
            // 落回 display 反查
        }
        return recipeIdOf(entry.display());
    }

    /**
     * 该显示所属配方的命名空间；查不到返回 {@code null}（调用方回退产物命名空间）。
     */
    public static String namespaceOf(RecipeDisplay display) {
        Identifier id = recipeIdOf(display);
        return id == null ? null : id.getNamespace();
    }

    // ================================================================
    //  来源解析（优先级：集成服务端 → Fabric 同步 → 本地缓存）
    // ================================================================

    private static void ensureFresh() {
        RecipeManager serverManager = integratedServerRecipeManager();
        if (serverManager != null) {
            if (Objects.equals(serverManager, builtIdentity)) return;
            rebuildFromRecipes(serverManager.getRecipes(), serverManager, "integrated-server");
            return;
        }

        SynchronizedRecipes synced = BrbeJeiBridge.syncedRecipesOrNull();
        if (synced != null) {
            if (Objects.equals(synced, builtIdentity)) return;
            rebuildFromRecipes(synced.recipes(), synced, "synced");
            return;
        }

        if (VanillaRecipeCache.hasEntries()) {
            Integer generation = VanillaRecipeCache.generation();
            if (Objects.equals(generation, builtIdentity)) return;
            rebuildFromCache(generation);
            return;
        }

        // 三处都没有（标题界面 / 数据未就绪 / 服务端不发配方且缓存为空）：清空 + 节流重试。
        if (builtIdentity == NO_SOURCE && System.currentTimeMillis() < nextRetryAt) return;
        synchronized (LOCK) {
            BY_DISPLAY.clear();
            AMBIGUOUS.clear();
        }
        builtIdentity = NO_SOURCE;
        nextRetryAt = System.currentTimeMillis() + RETRY_INTERVAL_MS;
    }

    private static RecipeManager integratedServerRecipeManager() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.getSingleplayerServer() == null) return null;
            return minecraft.getSingleplayerServer().getRecipeManager();
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    private static void rebuildFromRecipes(Collection<RecipeHolder<?>> recipes,
                                           Object identity, String source) {
        Map<RecipeDisplay, Identifier> map = new HashMap<>();
        Set<RecipeDisplay> ambiguous = new HashSet<>();
        int indexed = 0;
        int failed = 0;
        if (recipes != null) {
            for (RecipeHolder<?> holder : recipes) {
                if (holder == null) continue;
                Recipe<?> recipe = holder.value();
                if (recipe == null) continue;
                // 只关心 RBIP 会归组的类型：合成 + 熔炉系（数据包里的其他类型不进标签）。
                if (!(recipe instanceof CraftingRecipe) && !(recipe instanceof AbstractCookingRecipe)) {
                    continue;
                }
                Identifier id = holder.id() == null ? null : holder.id().identifier();
                if (id == null) continue;
                List<RecipeDisplay> displays;
                try {
                    displays = recipe.display();
                } catch (Exception | LinkageError e) {
                    failed++;   // 个别配方 display() 不可解析（缺 registry 上下文等）：跳过即可
                    continue;
                }
                if (displays == null) continue;
                for (RecipeDisplay display : displays) {
                    if (!isGroupedDisplay(display)) continue;
                    indexed++;
                    putDisplay(map, ambiguous, display, id);
                }
            }
        }
        Map<RecipeDisplay, Identifier> frozen = Map.copyOf(map);
        Set<RecipeDisplay> frozenAmbiguous = Set.copyOf(ambiguous);
        synchronized (LOCK) {
            BY_DISPLAY.clear();
            BY_DISPLAY.putAll(frozen);
            AMBIGUOUS.clear();
            AMBIGUOUS.addAll(frozenAmbiguous);
        }
        builtIdentity = identity;
        BrbeLogger.log("BRBE-RECIPE-NS", "index from {}: displays={} ambiguous={} unresolved={}",
                source, frozen.size(), frozenAmbiguous.size(), failed);
    }

    private static void rebuildFromCache(int generation) {
        Map<RecipeDisplay, Identifier> map = new HashMap<>();
        Set<RecipeDisplay> ambiguous = new HashSet<>();
        for (CacheableRecipeDisplayEntry cached : VanillaRecipeCache.entries()) {
            if (cached == null || cached.recipeKey() == null) continue;
            Identifier id = Identifier.tryParse(cached.recipeKey());
            if (id == null) continue;
            RecipeDisplay display;
            try {
                RecipeDisplayEntry entry = cached.toEntry(CACHE_ID);
                if (entry == null) continue;
                display = entry.display();
            } catch (Exception | LinkageError e) {
                continue;
            }
            if (!isGroupedDisplay(display)) continue;
            putDisplay(map, ambiguous, display, id);
        }
        Map<RecipeDisplay, Identifier> frozen = Map.copyOf(map);
        Set<RecipeDisplay> frozenAmbiguous = Set.copyOf(ambiguous);
        synchronized (LOCK) {
            BY_DISPLAY.clear();
            BY_DISPLAY.putAll(frozen);
            AMBIGUOUS.clear();
            AMBIGUOUS.addAll(frozenAmbiguous);
        }
        builtIdentity = generation;
        BrbeLogger.log("BRBE-RECIPE-NS", "index from classpath cache (gen {}): displays={} ambiguous={}",
                generation, frozen.size(), frozenAmbiguous.size());
    }

    /** 同名 display 有两个命名空间时记进歧义集（例如数据包原样复制了一份原版配方）。 */
    private static void putDisplay(Map<RecipeDisplay, Identifier> map, Set<RecipeDisplay> ambiguous,
                                   RecipeDisplay display, Identifier recipeId) {
        Identifier previous = map.putIfAbsent(display, recipeId);
        if (previous != null && !previous.equals(recipeId)
                && !previous.getNamespace().equals(recipeId.getNamespace())) {
            ambiguous.add(display);
        }
    }

    /** RBIP 会归组的显示类型：有序/无序合成 + 熔炉系（烟熏、高炉共用 {@code FurnaceRecipeDisplay}）。 */
    private static boolean isGroupedDisplay(RecipeDisplay display) {
        return display instanceof ShapedCraftingRecipeDisplay
                || display instanceof ShapelessCraftingRecipeDisplay
                || display instanceof FurnaceRecipeDisplay;
    }
}

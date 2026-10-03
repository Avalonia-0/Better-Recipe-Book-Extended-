package com.alonie.recipebookispain_extended;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.alonie.brbe.mixins.accessors.CreativeModeTabsAccessor;
import com.alonie.brbe.pin.TabPinManager;
import com.alonie.brbe.util.ModNameUtil;
import com.alonie.recipebookispain_extended.access.ItemAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.gui.screens.recipebook.SearchRecipeBookCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.ExtendedRecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import com.alonie.brbe.util.BrbeLogger;

public class RecipeBookIsPain {

    public static final Logger LOGGER = LogManager.getLogger("RBIP");
    public static PlatformAbstractions PLATFORM;
    public static boolean isOwOLoaded = false;

    public static final List<ExtendedRecipeBookCategory> CRAFTING_SEARCH_LIST = new ArrayList<>();
    public static final List<ExtendedRecipeBookCategory> CRAFTING_LIST = new ArrayList<>();

    public static final BiMap<ExtendedRecipeBookCategory, CreativeModeTab> RECIPE_BOOK_GROUP_TO_ITEM_GROUP = HashBiMap.create();
    public static final BiMap<ExtendedRecipeBookCategory, CreativeModeTab> FURNACE_BOOK_GROUP_TO_ITEM_GROUP = HashBiMap.create();
    public static final Set<CreativeModeTab> FURNACE_ACTIVE_TABS = new HashSet<>();
    public static final BiMap<ExtendedRecipeBookCategory, CreativeModeTab> SMOKER_BOOK_GROUP_TO_ITEM_GROUP = HashBiMap.create();
    public static final Set<CreativeModeTab> SMOKER_ACTIVE_TABS = new HashSet<>();
    public static final BiMap<ExtendedRecipeBookCategory, CreativeModeTab> BLAST_FURNACE_BOOK_GROUP_TO_ITEM_GROUP = HashBiMap.create();
    public static final Set<CreativeModeTab> BLAST_FURNACE_ACTIVE_TABS = new HashSet<>();
    public enum FurnaceVariant { FURNACE, SMOKER, BLAST_FURNACE }
    private static final List<CreativeModeTab> MIRRORED_ITEM_GROUPS = new ArrayList<>();
    private static boolean initialized;

    private static final Map<String, CreativeModeTab> namespaceCache = new HashMap<>();
    private static boolean namespaceCacheBuilt;

    /**
     * 配方书标签页覆盖映射。
     * 某些物品在原版创造标签页中的归属与配方书用户期望不符。
     * 例如红石火把归在"功能方块"标签，但作为红石元件应同时在"红石方块"标签可见。
     * <p>
     * 此映射将物品重定向到正确的标签页，使 RBIP 标签页模式下该物品出现在期望的标签页中。
     * Key: 物品注册ID, Value: 目标创造标签页的注册路径 (如 "redstone_blocks")
     */
    private static final Map<Identifier, String> TAB_OVERRIDES = new HashMap<>();

    static {
        // 红石火把：原版归在 functional_blocks，重定向到 redstone_blocks 使其在红石方块标签页可见
        // Redstone torch: vanilla places it in functional_blocks; override to
        // redstone_blocks so it appears in the redstone blocks tab under RBIP.
        TAB_OVERRIDES.put(Identifier.fromNamespaceAndPath("minecraft", "redstone_torch"), "redstone_blocks");
        TAB_OVERRIDES.put(Identifier.fromNamespaceAndPath("minecraft", "redstone_wall_torch"), "redstone_blocks");
        // 在此添加更多覆盖 —— Add more overrides here as needed.
    }

    // ------------------------------------------------
    //  Initialisation
    // ------------------------------------------------

    public static synchronized void ensureInitialized() {
        if (initialized) return;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) {
            BrbeLogger.log("RBIP", "Delaying recipe book init until a client world is available");
            return;
        }

        try {
            CreativeModeTabs.tryRebuildTabContents(FeatureFlags.DEFAULT_FLAGS, false, client.level.registryAccess());
        } catch (Exception e) {
            LOGGER.warn("[RBIP] Could not refresh creative item groups before building recipe book tabs", e);
        }
        // The eager rebuild above caches CACHED_PARAMETERS, which makes the
        // creative inventory screen skip its own rebuild (and skip building the
        // SessionSearchTrees search trees → all creative search results blank).
        // Null it so the game rebuilds on the creative screen.
        CreativeModeTabsAccessor.brbe$invalidateCachedParameters(null);

        // --- Phase 1: standard search-tab scanning ---
        CreativeModeTabs.allTabs().stream().filter(RecipeBookIsPain::shouldMirror).forEach(tab -> {
            try {
                tab.getSearchTabDisplayItems().stream()
                        .filter(stack -> !stack.isEmpty())
                        .map(ItemStack::getItem)
                        .map(ItemAccess.class::cast)
                        .filter(access -> access.rbip$getPossibleGroup().isEmpty())
                        .forEach(access -> access.rbip$setPossibleGroup(tab));

                ExtendedRecipeBookCategory recipeBookGroup = new RecipeBookCategory();
                RECIPE_BOOK_GROUP_TO_ITEM_GROUP.put(recipeBookGroup, tab);
                MIRRORED_ITEM_GROUPS.add(tab);
                CRAFTING_LIST.add(recipeBookGroup);
                CRAFTING_SEARCH_LIST.add(recipeBookGroup);
            } catch (Exception e) {
                LOGGER.error("[RBIP] Error while processing {} item group", tab.getDisplayName(), e);
            }
        });

        // --- Phase 1b: furnace-type-specific ExtendedRecipeBookCategory objects ---
        // Each furnace type gets its own set of category objects (same creative tabs, different keys)
        FURNACE_BOOK_GROUP_TO_ITEM_GROUP.clear();
        SMOKER_BOOK_GROUP_TO_ITEM_GROUP.clear();
        BLAST_FURNACE_BOOK_GROUP_TO_ITEM_GROUP.clear();
        for (CreativeModeTab tab : MIRRORED_ITEM_GROUPS) {
            FURNACE_BOOK_GROUP_TO_ITEM_GROUP.put(new RecipeBookCategory(), tab);
            SMOKER_BOOK_GROUP_TO_ITEM_GROUP.put(new RecipeBookCategory(), tab);
            BLAST_FURNACE_BOOK_GROUP_TO_ITEM_GROUP.put(new RecipeBookCategory(), tab);
        }

        initialized = true;

        // --- Phase 2: namespace-based override ---
        buildNamespaceCache();
        applyNamespaceOverrides();

        BrbeLogger.log("RBIP", "recipe book init complete; mirrored {} creative groups", MIRRORED_ITEM_GROUPS.size());
    }

    // ------------------------------------------------
    //  Namespace cache
    // ------------------------------------------------

    public static synchronized void buildNamespaceCache() {
        namespaceCache.clear();
        for (CreativeModeTab group : CreativeModeTabs.allTabs()) {
            if (group.getType() == CreativeModeTab.Type.INVENTORY
                    || group.getType() == CreativeModeTab.Type.HOTBAR
                    || group.getType() == CreativeModeTab.Type.SEARCH) {
                continue;
            }
            // 「管理员用品」也不参与命名空间路由（否则物品仍可能被路由到一个不显示的标签）
            if (isOpBlocksTab(group)) continue;
            Identifier regId = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(group);
            if (regId != null) {
                namespaceCache.put(regId.getPath(), group);
            }
            String displayKey = normalizeGroupName(group.getDisplayName().getString());
            if (!displayKey.isEmpty()) {
                namespaceCache.put(displayKey, group);
            }
        }
        namespaceCacheBuilt = true;
        // 创造标签集合变了（Polymer 之类会重建）→ 物品→标签 索引跟着失效
        invalidateItemTabsIndex();
    }

    public static CreativeModeTab lookupByNamespace(String itemNamespace) {
        if (!namespaceCacheBuilt) buildNamespaceCache();
        return namespaceCache.get(itemNamespace);
    }

    public static synchronized void applyNamespaceOverrides() {
        if (!namespaceCacheBuilt) buildNamespaceCache();
        int overridden = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) continue;
            CreativeModeTab group = namespaceCache.get(id.getNamespace());
            if (group == null) continue;
            ((ItemAccess) item).rbip$setPossibleGroup(group);
            overridden++;
        }
        if (overridden > 0) {
            BrbeLogger.log("RBIP", "Namespace override: {} items rerouted to their own creative tabs", overridden);
        }
    }

    public static int getNamespaceCacheSize() {
        return namespaceCache.size();
    }

    private static String normalizeGroupName(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9_\\u4e00-\\u9fff]", "");
    }

    // ------------------------------------------------
    //  Group mirroring helpers
    // ------------------------------------------------

    /** 原版「管理员用品」标签的注册 id（{@code minecraft:op_blocks}）—— 用户 2026-10-01 要求直接屏蔽。 */
    private static final Identifier OP_BLOCKS_TAB_ID =
            Identifier.fromNamespaceAndPath("minecraft", "op_blocks");

    private static boolean shouldMirror(CreativeModeTab tab) {
        if (tab.getType() == CreativeModeTab.Type.INVENTORY
                || tab.getType() == CreativeModeTab.Type.HOTBAR
                || tab.getType() == CreativeModeTab.Type.SEARCH) {
            return false;
        }
        // 「管理员用品」不镜像（用户 2026-10-01）：那是创造模式给 OP 的物品总表（命令方块 /
        // 屏障 / 结构方块 / 调试棒…），在配方书里没有意义；屏蔽后这些物品（基本都没有配方）
        // 也不会再被它单独带出一个标签。
        return !isOpBlocksTab(tab);
    }

    /** 是不是原版「管理员用品」标签（按**注册 id** 判定，不受本地化显示名影响）。 */
    private static boolean isOpBlocksTab(CreativeModeTab tab) {
        try {
            return OP_BLOCKS_TAB_ID.equals(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab));
        } catch (Exception | LinkageError e) {
            return false;
        }
    }

    public static void registerNewGroup(CreativeModeTab group) {
        if (!shouldMirror(group)) return;
        if (RECIPE_BOOK_GROUP_TO_ITEM_GROUP.inverse().containsKey(group)) return;
        ExtendedRecipeBookCategory rg = new RecipeBookCategory();
        RECIPE_BOOK_GROUP_TO_ITEM_GROUP.put(rg, group);
        MIRRORED_ITEM_GROUPS.add(group);
        CRAFTING_LIST.add(rg);
        CRAFTING_SEARCH_LIST.add(rg);
        // 标签集合变了 → 「来源 → 创造标签」「物品 → 创造标签」两个索引失效（惰性重建）
        invalidateItemTabsIndex();
        BrbeLogger.log("RBIP", "Late-registered group: {}", group.getDisplayName().getString());
    }

    public static int getMirroredGroupCount() {
        return MIRRORED_ITEM_GROUPS.size();
    }

    // ------------------------------------------------
    //  Lookup
    // ------------------------------------------------

    public static CreativeModeTab toItemGroup(ExtendedRecipeBookCategory recipeBookGroup) {
        CreativeModeTab tab = RECIPE_BOOK_GROUP_TO_ITEM_GROUP.get(recipeBookGroup);
        if (tab != null) return tab;
        tab = FURNACE_BOOK_GROUP_TO_ITEM_GROUP.get(recipeBookGroup);
        if (tab != null) return tab;
        tab = SMOKER_BOOK_GROUP_TO_ITEM_GROUP.get(recipeBookGroup);
        if (tab != null) return tab;
        tab = BLAST_FURNACE_BOOK_GROUP_TO_ITEM_GROUP.get(recipeBookGroup);
        if (tab != null) return tab;
        // 数据包标签模式没有"代表创造标签"：图标是随机产物（原版草方块），
        // pin 键是包 id —— 一律返回 null（owo 图标委托 / 解锁弹跳动画据此跳过）。
        return null;
    }

    public static ExtendedRecipeBookCategory toRecipeBookGroup(CreativeModeTab tab) {
        return RECIPE_BOOK_GROUP_TO_ITEM_GROUP.inverse().get(tab);
    }

    /**
     * 创造模式档：物品对应的**第一个**配方书分类（单值路径；多值见
     * {@link #toRecipeBookGroups(ItemStack)}）。
     */
    public static ExtendedRecipeBookCategory toRecipeBookGroup(ItemStack stack) {
        List<ExtendedRecipeBookCategory> groups = toRecipeBookGroups(stack);
        return groups.isEmpty() ? null : groups.get(0);
    }

    /**
     * 创造模式档：物品对应的**全部**配方书分类 —— 原版创造模式允许同一物品挂在**多个**标签下
     * （木桶同时在「功能方块」和「红石方块」），用户 2026-10-01 要求"还原优先于去重"：
     * 一条配方要出现在产物所属的**每一个**标签里，而不是只挑一个。
     */
    public static List<ExtendedRecipeBookCategory> toRecipeBookGroups(ItemStack stack) {
        ensureInitialized();
        if (stack == null || stack.isEmpty()) return List.of();
        List<ExtendedRecipeBookCategory> groups = new ArrayList<>(2);
        for (CreativeModeTab tab : candidateTabs(stack)) {
            addDistinctGroup(groups, toRecipeBookGroup(tab));
        }
        return groups;
    }

    /** 创造模式档（熔炉系）：物品对应的**全部**配方书分类（每个熔炉类型一套分类对象）。 */
    public static List<ExtendedRecipeBookCategory> toFurnaceRecipeBookGroups(ItemStack stack, FurnaceVariant type) {
        ensureInitialized();
        if (stack == null || stack.isEmpty()) return List.of();
        BiMap<ExtendedRecipeBookCategory, CreativeModeTab> groupMap = switch (type) {
            case SMOKER -> SMOKER_BOOK_GROUP_TO_ITEM_GROUP;
            case BLAST_FURNACE -> BLAST_FURNACE_BOOK_GROUP_TO_ITEM_GROUP;
            default -> FURNACE_BOOK_GROUP_TO_ITEM_GROUP;
        };
        List<ExtendedRecipeBookCategory> groups = new ArrayList<>(2);
        for (CreativeModeTab tab : candidateTabs(stack)) {
            addDistinctGroup(groups, groupMap.inverse().get(tab));
        }
        return groups;
    }

    private static void addDistinctGroup(List<ExtendedRecipeBookCategory> groups,
                                         ExtendedRecipeBookCategory group) {
        if (group != null && !groups.contains(group)) groups.add(group);
    }

    /**
     * 物品**可能**归属的全部创造标签（保序去重），多标签归组的公共输入：
     *
     * <ol>
     *   <li>{@link #TAB_OVERRIDES} 里的覆盖标签（红石火把 → 红石方块）——**额外**加入。
     *       旧行为是"替换"真实归属，用户 2026-10-01 定"还原优先于去重"，改成叠加；</li>
     *   <li>创造模式物品栏里**真实**包含该物品的全部标签（{@link #tabsForItem}，多值）；</li>
     *   <li>索引不可用（未进世界 / 标签内容没建起来）→ 退回该命名空间的代表标签；</li>
     *   <li>最后兜底：物品自己声明的可能标签（Phase 1 扫描 / {@code applyNamespaceOverrides}）。</li>
     * </ol>
     */
    private static List<CreativeModeTab> candidateTabs(ItemStack stack) {
        List<CreativeModeTab> out = new ArrayList<>(2);
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null) {
            String overrideTabPath = TAB_OVERRIDES.get(id);
            if (overrideTabPath != null) {
                CreativeModeTab overrideTab = namespaceCache.get(overrideTabPath);
                if (overrideTab != null) out.add(overrideTab);
            }
        }
        for (CreativeModeTab tab : tabsForItem(stack)) {
            if (!out.contains(tab)) out.add(tab);
        }
        if (out.isEmpty() && id != null) {
            CreativeModeTab nsGroup = namespaceCache.get(id.getNamespace());
            if (nsGroup != null) out.add(nsGroup);
        }
        if (out.isEmpty()) {
            ((ItemAccess) stack.getItem()).rbip$getPossibleGroup().ifPresent(out::add);
        }
        return out;
    }

    // ------------------------------------------------
    //  Item → creative tabs （物品挂在哪些创造标签下）
    // ------------------------------------------------

    /**
     * 物品 → 包含它的创造标签集合。原版允许**同一物品挂在多个创造标签下**
     * （木桶 = 功能方块 + 红石方块），而 RBIP 原来只按一个标签归组 → 配方书里木桶配方
     * 只出现在「功能方块」（用户 2026-10-01 反馈）。这个反向索引让一条配方能进到
     * 产物所属的每一个标签。
     *
     * <p>惰性建立；建成但为空（标签内容还没建）时按 {@link #ITEM_TABS_RETRY_MS} 节流重试；
     * {@link #buildNamespaceCache()}（初始化 / Polymer 刷新）会让它失效重建。</p>
     */
    private static final Map<Item, Set<CreativeModeTab>> ITEM_TABS = new HashMap<>();
    private static boolean itemTabsBuilt;
    private static long itemTabsNextRetryAt;
    private static int itemTabsAttempts;
    private static final long ITEM_TABS_RETRY_MS = 1_000L;
    /** 建成空索引时的最大重试次数（防"内容一直建不起来"时每秒重建一遍创造标签）。 */
    private static final int ITEM_TABS_MAX_ATTEMPTS = 3;

    /** 索引失效（下一次查询重建）。 */
    public static synchronized void invalidateItemTabsIndex() {
        synchronized (ITEM_TABS) {
            ITEM_TABS.clear();
        }
        synchronized (TAB_NAMESPACES) {
            TAB_NAMESPACES.clear();
        }
        synchronized (SOURCE_TABS) {
            SOURCE_TABS.clear();
        }
        itemTabsBuilt = false;
        itemTabsNextRetryAt = 0L;
        itemTabsAttempts = 0;
    }

    /** 包含该物品的**全部**创造标签（保序去重）；索引不可用或该物品不在任何标签里 → 空表。 */
    public static List<CreativeModeTab> tabsForItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return List.of();
        ensureItemTabsIndex();
        synchronized (ITEM_TABS) {
            Set<CreativeModeTab> tabs = ITEM_TABS.get(stack.getItem());
            return tabs == null ? List.of() : List.copyOf(tabs);
        }
    }

    private static void ensureItemTabsIndex() {
        if (itemTabsBuilt && !ITEM_TABS.isEmpty()) return;
        if (itemTabsAttempts >= ITEM_TABS_MAX_ATTEMPTS) return;
        long now = System.currentTimeMillis();
        if (itemTabsBuilt && now < itemTabsNextRetryAt) return;
        if (itemTabsBuilt) itemTabsNextRetryAt = now + ITEM_TABS_RETRY_MS;
        itemTabsAttempts++;
        buildItemTabsIndex();
    }

    /**
     * 建「物品 → 创造标签们」索引：遍历每个**镜像**创造标签的
     * {@link CreativeModeTab#getDisplayItems()}（一个标签里全部物品）。
     *
     * <p>那份列表由 {@code CreativeModeTabs.tryRebuildTabContents} 填充（vanilla 在打开创造
     * 模式物品栏时才建），所以这里先自己调一次（与 vanilla 同参数，幂等），拿不到就留空
     * —— 调用方退回单标签路径。</p>
     */
    public static synchronized void buildItemTabsIndex() {
        ensureInitialized();
        tryRebuildCreativeTabContents();
        Map<Item, Set<CreativeModeTab>> index = new HashMap<>();
        Map<CreativeModeTab, Set<String>> tabNamespaces = new HashMap<>();
        int tabs = 0;
        for (CreativeModeTab tab : MIRRORED_ITEM_GROUPS) {
            if (tab == null) continue;
            Collection<ItemStack> items;
            try {
                items = tab.getDisplayItems();
            } catch (Exception | LinkageError e) {
                continue;
            }
            if (items == null || items.isEmpty()) continue;
            tabs++;
            Set<String> namespaces = new LinkedHashSet<>();
            for (ItemStack stack : items) {
                if (stack == null || stack.isEmpty()) continue;
                Item item = stack.getItem();
                Set<CreativeModeTab> set = index.get(item);
                if (set == null) {
                    set = new LinkedHashSet<>();
                    index.put(item, set);
                }
                set.add(tab);
                Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
                if (itemId != null) namespaces.add(itemId.getNamespace());
            }
            if (!namespaces.isEmpty()) tabNamespaces.put(tab, namespaces);
        }
        synchronized (ITEM_TABS) {
            ITEM_TABS.clear();
            ITEM_TABS.putAll(index);
        }
        synchronized (TAB_NAMESPACES) {
            TAB_NAMESPACES.clear();
            TAB_NAMESPACES.putAll(tabNamespaces);
        }
        // 索引重建（含「内容还没建起来 → 每秒重试」那几次）→ 它的派生缓存一起失效，
        // 否则第一次在空索引上算出的候选标签会被永久缓存。
        synchronized (SOURCE_TABS) {
            SOURCE_TABS.clear();
        }
        itemTabsBuilt = true;
        BrbeLogger.log("RBIP", "creative item→tabs index: {} items from {} tabs", index.size(), tabs);
    }

    /**
     * 自己触发一次 vanilla 的创造标签内容重建（参数与创造模式物品栏一致；幂等）。
     *
     * <p>随后必须把缓存的参数清掉（与 {@code ensureInitialized} 同款）：否则创造模式界面
     * 会跳过它自己的重建，连带跳过 {@code SessionSearchTrees} 搜索树 → 创造模式搜索空白。</p>
     */
    private static void tryRebuildCreativeTabContents() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.player == null) return;
        try {
            net.minecraft.world.flag.FeatureFlagSet flags = client.getConnection() != null
                    ? client.getConnection().enabledFeatures()
                    : FeatureFlags.DEFAULT_FLAGS;
            CreativeModeTabs.tryRebuildTabContents(flags,
                    client.player.canUseGameMasterBlocks(), client.level.registryAccess());
            CreativeModeTabsAccessor.brbe$invalidateCachedParameters(null);
        } catch (Exception | LinkageError e) {
            BrbeLogger.log("RBIP", "creative tab rebuild before item→tabs index failed: {}", e.toString());
        }
    }

    // ------------------------------------------------
    //  Source → creative tabs （来源（模组 / 命名空间）→ 它的创造标签）
    // ------------------------------------------------

    /** 创造标签 → 该标签里物品的命名空间集合（「这个标签属于哪个模组」的物品视角）。 */
    private static final Map<CreativeModeTab, Set<String>> TAB_NAMESPACES = new HashMap<>();
    /** 来源（命名空间 / 模组 id）→ 它的候选创造标签（保序，已按物品栏序号升序）。 */
    private static final Map<String, List<CreativeModeTab>> SOURCE_TABS = new HashMap<>();

    /**
     * 某个**来源**（命名空间档的命名空间 / 数据包档里数据包归属的模组 id）在创造模式物品栏里
     * 对应的标签 —— 用户 2026-10-01 定的两条模式规则都建立在它之上：**序号最靠前**的那个
     * 提供标签图标；**只有一个候选**时连 tooltip 也用那个创造标签的。
     *
     * <p>判定顺序（保序，顺序即创造模式物品栏序号）：</p>
     * <ol>
     *   <li>该来源**自己注册**的创造标签（注册 id 的命名空间 == 来源）——最精确的信号；</li>
     *   <li>它一个都没有时退而求其次：**包含该命名空间物品**的创造标签（靠
     *       {@link #buildItemTabsIndex()} 的标签→命名空间反向表）；</li>
     *   <li>**原版标签（{@code minecraft:*}）一律不算候选** —— 模组把物品直接塞进原版标签时
     *       （用户 2026-10-01 提到的"归于原版标签"），该标签的图标与名字都不属于这个模组，
     *       应当退回"在该命名空间的配方里按规则抽取产物图标"。</li>
     * </ol>
     */
    public static List<CreativeModeTab> sourceCreativeTabs(String key) {
        if (key == null || key.isBlank() || key.charAt(0) == '#') return List.of();
        synchronized (SOURCE_TABS) {
            List<CreativeModeTab> cached = SOURCE_TABS.get(key);
            if (cached != null) return cached;
        }
        ensureItemTabsIndex();
        List<CreativeModeTab> owned = new ArrayList<>(2);
        List<CreativeModeTab> holding = new ArrayList<>(2);
        for (CreativeModeTab tab : MIRRORED_ITEM_GROUPS) {
            if (tab == null || isVanillaCreativeTab(tab)) continue;
            Identifier id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            if (id != null && key.equals(id.getNamespace())) {
                owned.add(tab);
            } else if (tabHoldsNamespace(tab, key)) {
                holding.add(tab);
            }
        }
        List<CreativeModeTab> out = List.copyOf(owned.isEmpty() ? holding : owned);
        synchronized (SOURCE_TABS) {
            SOURCE_TABS.put(key, out);
        }
        return out;
    }

    /** 该标签是不是**原版**标签（注册 id 在 {@code minecraft} 命名空间）。 */
    private static boolean isVanillaCreativeTab(CreativeModeTab tab) {
        try {
            Identifier id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            return id != null && "minecraft".equals(id.getNamespace());
        } catch (Exception | LinkageError e) {
            return true;        // 判不出来就当原版：宁可退回产物图标
        }
    }

    private static boolean tabHoldsNamespace(CreativeModeTab tab, String namespace) {
        synchronized (TAB_NAMESPACES) {
            Set<String> namespaces = TAB_NAMESPACES.get(tab);
            return namespaces != null && namespaces.contains(namespace);
        }
    }

    /** 创造标签的图标物品（创造模式物品栏里画的那个）；取不到返回空。 */
    private static ItemStack creativeTabIcon(CreativeModeTab tab) {
        try {
            ItemStack icon = tab == null ? null : tab.getIconItem();
            return icon == null ? ItemStack.EMPTY : icon.copyWithCount(1);
        } catch (Exception | LinkageError e) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * 该来源的**代表图标** = 候选创造标签里**序号最靠前**那个的图标；没有候选标签 → 空
     * （调用方退回产物图标：命名空间档 = 该命名空间的产物池，数据包档 = 该包的产物池）。
     */
    private static ItemStack sourceIconStack(String key) {
        for (CreativeModeTab tab : sourceCreativeTabs(key)) {
            ItemStack icon = creativeTabIcon(tab);
            if (!icon.isEmpty()) return icon;
        }
        return ItemStack.EMPTY;
    }

    /**
     * 「候选标签只有一个」的**特例**（用户 2026-10-01）：这种配方书标签事实上就是那一个创造标签，
     * tooltip 也改用该创造标签的 tooltip（{@link CreativeModeTab#getDisplayName()}）；
     * 候选不止一个或一个都没有 → 返回 {@code null}（走各档自己的 tooltip 规则）。
     */
    private static Component singleCandidateTabTooltip(String key) {
        List<CreativeModeTab> candidates = sourceCreativeTabs(key);
        if (candidates.size() != 1) return null;
        try {
            Component name = candidates.get(0).getDisplayName();
            return name == null || name.getString().isBlank() ? null : name;
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    // ------------------------------------------------
    //  Tab building
    // ------------------------------------------------

    public static List<RecipeBookComponent.TabInfo> withCreativeTabs(List<RecipeBookComponent.TabInfo> tabs) {
        ensureInitialized();
        List<RecipeBookComponent.TabInfo> expandedTabs = new ArrayList<>();
        tabs.stream()
                .filter(tab -> tab.category() instanceof SearchRecipeBookCategory)
                .findFirst()
                .ifPresent(expandedTabs::add);

        // 固定的创造标签排在最前（首页、搜索标签之下，按 pin 顺序），其余按自然顺序。
        List<CreativeModeTab> pinned = TabPinManager.pinnedTabs();
        for (CreativeModeTab tab : pinned) {
            if (!MIRRORED_ITEM_GROUPS.contains(tab)) continue;
            Optional.ofNullable(toRecipeBookGroup(tab))
                    .map(group -> new RecipeBookComponent.TabInfo(tab.getIconItem(), Optional.empty(), group))
                    .ifPresent(expandedTabs::add);
        }
        for (CreativeModeTab tab : MIRRORED_ITEM_GROUPS) {
            if (pinned.contains(tab)) continue;
            Optional.ofNullable(toRecipeBookGroup(tab))
                    .map(group -> new RecipeBookComponent.TabInfo(tab.getIconItem(), Optional.empty(), group))
                    .ifPresent(expandedTabs::add);
        }
        return expandedTabs;
    }

    // ------------------------------------------------
    //  Data pack tabs （数据包标签：一个来源包一个标签）
    // ------------------------------------------------

    /**
     * 数据包标签的注册表：**每个来源包一个分类对象**，按需创建、**身份稳定**
     * （{@code collectionsByTab} 的键按身份比较，不能每次重建都 new 一个）。
     *
     * <p>归组键 = {@link com.alonie.brbe.cache.RecipePackIndex} 给出的包 id
     * （{@code vanilla} / 模组自带数据包 / 每个启用的数据包）。单机之外拿不到服务端包列表时
     * 全部配方进 {@link com.alonie.brbe.cache.RecipePackIndex#UNKNOWN_KEY} —— 一个
     * 「服务器」标签（用户 2026-10-01 定）。</p>
     *
     * <p>四种配方书类型各有一套（合成 / 熔炉 / 烟熏炉 / 高炉），与 {@code withCreativeTabs}
     * 那一套 per-creative-tab 的分类互不干扰：两套同时存在，靠配置开关决定谁被用。</p>
     */
    private static final Map<String, ExtendedRecipeBookCategory> PACK_CRAFTING_GROUPS = new LinkedHashMap<>();
    private static final Map<String, ExtendedRecipeBookCategory> PACK_FURNACE_GROUPS = new LinkedHashMap<>();
    private static final Map<String, ExtendedRecipeBookCategory> PACK_SMOKER_GROUPS = new LinkedHashMap<>();
    private static final Map<String, ExtendedRecipeBookCategory> PACK_BLAST_GROUPS = new LinkedHashMap<>();
    /** 数据包分类 → 包 id（四套共用）：判定标签、取图标、取 tooltip、pin 都靠它。 */
    private static final Map<ExtendedRecipeBookCategory, String> PACK_GROUP_KEY = new HashMap<>();
    /** 该熔炉类型下**已经有配方**的包（供 {@code withPackFurnaceTabs} 过滤空标签）。 */
    public static final Set<String> PACK_FURNACE_ACTIVE = new HashSet<>();
    public static final Set<String> PACK_SMOKER_ACTIVE = new HashSet<>();
    public static final Set<String> PACK_BLAST_ACTIVE = new HashSet<>();
    /** 包 id → 该标签下出现过的产物物品（**图标随机池**；按书里的顺序收集，封顶
     *  {@link #ICON_POOL_CAP} 条，避免大包吃掉太多内存）。 */
    private static final Map<String, List<ItemStack>> packIconPool = new HashMap<>();
    private static final int ICON_POOL_CAP = 64;

    /** 数据包标签模式（配置项「标签模式」= 数据包；默认是创造模式物品栏那一档）。
     *  联机时恒 false（配置层把数据包档降级成命名空间档，见 {@code effectiveTabMode()}）。 */
    public static boolean datapackModeEnabled() {
        return RecipeBookIsPainExtendedConfig.isDatapackMode();
    }

    /** 命名空间标签模式（配置项「标签模式」= 命名空间；联机时数据包档也走这一档）。 */
    public static boolean namespaceModeEnabled() {
        return RecipeBookIsPainExtendedConfig.isNamespaceMode();
    }

    /**
     * 合成台标签栏（按当前生效档位）：
     * 创造模式物品栏档 = 每个创造标签一个书标签；命名空间档 = 搜索 + 每个命名空间一个；
     * 数据包档 = 搜索 + 每个来源数据包一个。
     */
    public static List<RecipeBookComponent.TabInfo> craftingTabsForCurrentMode(
            List<RecipeBookComponent.TabInfo> vanillaTabs, List<RecipeBookComponent.TabInfo> currentTabs) {
        if (namespaceModeEnabled()) return withNamespaceTabs(vanillaTabs);
        if (datapackModeEnabled()) return withPackTabs(vanillaTabs);
        return withCreativeTabs(currentTabs);
    }

    /** 熔炉系（熔炉 / 烟熏炉 / 高炉）标签栏（按当前生效档位），语义同
     *  {@link #craftingTabsForCurrentMode}。 */
    public static List<RecipeBookComponent.TabInfo> furnaceTabsForCurrentMode(
            List<RecipeBookComponent.TabInfo> vanillaTabs, List<RecipeBookComponent.TabInfo> currentTabs,
            FurnaceVariant type) {
        if (namespaceModeEnabled()) return withNamespaceFurnaceTabs(vanillaTabs, type);
        if (datapackModeEnabled()) return withPackFurnaceTabs(vanillaTabs, type);
        return withFurnaceCreativeTabs(currentTabs, type);
    }

    private static Map<String, ExtendedRecipeBookCategory> packGroupMap(FurnaceVariant variant) {
        if (variant == null) return PACK_CRAFTING_GROUPS;
        return switch (variant) {
            case SMOKER -> PACK_SMOKER_GROUPS;
            case BLAST_FURNACE -> PACK_BLAST_GROUPS;
            default -> PACK_FURNACE_GROUPS;
        };
    }

    private static synchronized ExtendedRecipeBookCategory packGroup(String key, FurnaceVariant variant) {
        Map<String, ExtendedRecipeBookCategory> map = packGroupMap(variant);
        ExtendedRecipeBookCategory group = map.get(key);
        if (group == null) {
            group = new RecipeBookCategory();
            map.put(key, group);
            PACK_GROUP_KEY.put(group, key);
        }
        return group;
    }

    /**
     * 归组键解析（数据包模式的主路径）：**配方 id → 提供它的数据包**。
     *
     * <ol>
     *   <li>配方 id 反查（{@link com.alonie.brbe.cache.RecipeNamespaceIndex}）→ 包索引
     *       （{@link com.alonie.brbe.cache.RecipePackIndex}）；</li>
     *   <li>联机（拿不到包列表）→ 一律 {@link com.alonie.brbe.cache.RecipePackIndex#UNKNOWN_KEY}
     *       「服务器」标签；</li>
     *   <li>单机但该 display 反查不到配方 id → 返回 {@code null}，调用方退回**产物命名空间**
     *       （{@link #toPackGroup(ItemStack, FurnaceVariant)}）。</li>
     * </ol>
     */
    /** 数据包档归属（**条目版**）：注入条目（负 id）用本地缓存的配方 key，其余走 display 反查。 */
    /** 该条目的**全部**提供者（资源栈顺序，胜者在最前）；查不到时退化为"单个兜底 key"。
     *  用户 2026-10-02 定：同一条配方被多个包覆盖时，**每个提供者的标签里都显示一份**
     *  （实测 Stellarity 与 Incendium 都覆盖 5 条原版红石配方）。展示内容仍以资源栈胜者为准。 */
    public static java.util.List<String> packKeysOfRecipeEntry(
            net.minecraft.world.item.crafting.display.RecipeDisplayEntry entry) {
        Identifier recipeId = com.alonie.brbe.cache.RecipeNamespaceIndex.recipeIdOfEntry(entry);
        java.util.List<String> providers = com.alonie.brbe.cache.RecipePackIndex.packKeysOf(recipeId);
        if (!providers.isEmpty()) return providers;
        if (!com.alonie.brbe.cache.RecipePackIndex.available()) {
            return java.util.List.of(com.alonie.brbe.cache.RecipePackIndex.UNKNOWN_KEY);
        }
        String fallback = recipeId == null ? null : packKeyForNamespace(recipeId.getNamespace());
        return fallback == null ? java.util.List.of() : java.util.List.of(fallback);
    }

    public static String packKeyOfRecipeEntry(
            net.minecraft.world.item.crafting.display.RecipeDisplayEntry entry) {
        Identifier recipeId = com.alonie.brbe.cache.RecipeNamespaceIndex.recipeIdOfEntry(entry);
        String key = com.alonie.brbe.cache.RecipePackIndex.packKeyOf(recipeId);
        if (key != null) return key;
        if (!com.alonie.brbe.cache.RecipePackIndex.available()) {
            return com.alonie.brbe.cache.RecipePackIndex.UNKNOWN_KEY;
        }
        return recipeId == null ? null : packKeyForNamespace(recipeId.getNamespace());
    }

    public static String packKeyOfRecipe(net.minecraft.world.item.crafting.display.RecipeDisplay display) {
        Identifier recipeId = com.alonie.brbe.cache.RecipeNamespaceIndex.recipeIdOf(display);
        String key = com.alonie.brbe.cache.RecipePackIndex.packKeyOf(recipeId);
        if (key != null) return key;
        if (!com.alonie.brbe.cache.RecipePackIndex.available()) {
            return com.alonie.brbe.cache.RecipePackIndex.UNKNOWN_KEY;
        }
        return recipeId == null ? null : packKeyForNamespace(recipeId.getNamespace());
    }

    /** 命名空间 → 分组键：单机优先用「该命名空间下配方最多的包」，拿不到就用命名空间本身。 */
    public static String packKeyForNamespace(String namespace) {
        if (namespace == null) return null;
        String owner = com.alonie.brbe.cache.RecipePackIndex.namespaceOwner(namespace);
        return owner != null ? owner : namespace;
    }

    /**
     * **回退路径**：按产物物品的命名空间归组（display 反查不到配方 id 时才用）。
     *
     * @param variant 熔炉类型；合成传 {@code null}
     */
    public static ExtendedRecipeBookCategory toPackGroup(ItemStack stack, FurnaceVariant variant) {
        if (stack == null || stack.isEmpty()) return null;
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return null;
        return packGroupFor(packKeyForNamespace(id.getNamespace()), variant);
    }

    /** 按**分组键**取（或建）数据包标签分类 —— 归组键就是包 id（或 {@code #unknown}）。 */
    public static synchronized ExtendedRecipeBookCategory packGroupFor(String key, FurnaceVariant variant) {
        if (key == null) return null;
        return packGroup(key, variant);
    }

    /**
     * 记下该标签下出现过的产物物品（图标随机池）。
     *
     * <p>图标规则（用户 2026-10-01 定）：**原版保留草方块特例**，其它包从池里**稳定伪随机**挑一个
     * ——种子 = 包 id + 世界盐（{@link com.alonie.brbe.util.WorldScopedStore#salt()}），
     * 同一存档内不变、不会随重建跳动，换世界才可能换一个。</p>
     */
    public static synchronized void rememberPackProduct(String key, ItemStack stack) {
        if (key == null || stack == null || stack.isEmpty()) return;
        List<ItemStack> pool = packIconPool.computeIfAbsent(key, k -> new ArrayList<>());
        if (pool.size() >= ICON_POOL_CAP) return;
        pool.add(stack.copyWithCount(1));
    }

    /**
     * 该分类是不是数据包模式下的**原版**标签（包 id 含 {@code vanilla}，或联机退回命名空间时的
     * {@code minecraft}）——图标固定草方块，也不走 owo 的创造标签渲染。
     */
    public static boolean isVanillaPackGroup(ExtendedRecipeBookCategory category) {
        return isVanillaPackKey(PACK_GROUP_KEY.get(category));
    }

    private static boolean isVanillaPackKey(String key) {
        // 精确匹配原版包 id（26.x = "vanilla"；联机退回命名空间时是 "minecraft"）——
        // 不用 contains，避免名字里带 vanilla 的数据包被误判成原版。
        return key != null && ("vanilla".equals(key) || key.endsWith("/vanilla")
                || "minecraft".equals(key));
    }

    /** 该分类是不是数据包标签。 */
    public static boolean isPackGroup(ExtendedRecipeBookCategory category) {
        return category != null && PACK_GROUP_KEY.containsKey(category);
    }

    /** 数据包标签的包 id（或 {@code #unknown}）；不是数据包标签时返回 {@code null}。 */
    public static String packKey(ExtendedRecipeBookCategory category) {
        return PACK_GROUP_KEY.get(category);
    }

    /** 数据包标签的 **pin 键** = 分组键本身（包 id / {@code #unknown}）。 */
    public static String packPinKey(ExtendedRecipeBookCategory category) {
        return PACK_GROUP_KEY.get(category);
    }

    /** 标签排列顺序：**原版包永远第一**（用户 2026-10-01），其余按资源栈顺序
     *  （原版 → 模组 → 数据包），同序按包 id 字典序；「服务器 / 未知来源」永远最后。
     *  pin 的另算（见 {@code appendExtendedTabs}）。 */
    private static List<String> packKeyOrder(java.util.Collection<String> keys) {
        List<String> order = new ArrayList<>(keys);
        order.sort((a, b) -> {
            int cmp = Integer.compare(packSortRank(a), packSortRank(b));
            if (cmp != 0) return cmp;
            boolean aUnknown = isUnknownKey(a);
            boolean bUnknown = isUnknownKey(b);
            if (aUnknown != bUnknown) return aUnknown ? 1 : -1;
            return a.compareTo(b);
        });
        return order;
    }

    private static int packSortRank(String key) {
        if (key == null) return Integer.MAX_VALUE;
        if (isVanillaPackKey(key)) return -1;                   // 原版标签的序号必须最靠前
        if (isUnknownKey(key)) return Integer.MAX_VALUE;        // 「服务器 / 未知来源」最后
        return com.alonie.brbe.cache.RecipePackIndex.orderOf(key);
    }

    private static boolean isUnknownKey(String key) {
        return key != null && key.charAt(0) == '#';
    }

    /**
     * 标签图标：**原版 = 草方块**（用户保留的特例）；**数据包归属于模组**
     * （包 id 就是模组 id，见 {@link com.alonie.brbe.util.ModNameUtil#isModId(String)}）时用
     * 该模组**序号最靠前的创造标签**的图标；其余从图标池里稳定伪随机挑一个产物；
     * 池空（没解析出任何产物）时兜底知识之书，避免标签凭空消失。
     */
    private static ItemStack packIconStack(String key) {
        if (isVanillaPackKey(key)) return new ItemStack(Items.GRASS_BLOCK);
        if (com.alonie.brbe.util.ModNameUtil.isModId(key)) {
            ItemStack tabIcon = sourceIconStack(key);
            if (!tabIcon.isEmpty()) return tabIcon;
        }
        List<ItemStack> pool = packIconPool.get(key);
        if (pool != null && !pool.isEmpty()) {
            int index = Math.floorMod(key.hashCode() * 31
                    + com.alonie.brbe.util.WorldScopedStore.salt(), pool.size());
            return pool.get(index).copy();
        }
        return new ItemStack(Items.KNOWLEDGE_BOOK);
    }

    /**
     * 数据包模式**合成台**标签栏：**搜索标签 + 每个来源包一个标签**。
     *
     * <p>原版配方书标签（建筑方块 / 红石 / 装备 / 杂项）不再出现 —— 原版配方与其他包一样，
     * 集中在自己那一个标签里（图标草方块）。固定（pin）过的标签排到标签块最前面。</p>
     */
    public static List<RecipeBookComponent.TabInfo> withPackTabs(List<RecipeBookComponent.TabInfo> vanillaTabs) {
        ensureInitialized();
        ensureExtendedPinKeysMigrated();
        prewarmPackGroups();
        List<RecipeBookComponent.TabInfo> tabs = extendedBaseTabs(vanillaTabs);
        appendPackTabs(tabs, PACK_CRAFTING_GROUPS.keySet(), null);
        return tabs;
    }

    /**
     * 标签栏的键来自**已被创建的分组对象**（`PACK_CRAFTING_GROUPS.keySet()`），而分组是
     * **集合构建**时才按需创建的。原版 `RecipeBookComponent.initVisuals` 先 `updateTabs()`
     * 再 `updateCollections()` ⇒ **首次打开配方书时分组表还是空的 → 标签集体消失**，重开一次
     * （此时分组已存在）才正常。切档位（改配置后不重启配方书）与每次进存档后第一次开书都会命中
     * （用户 2026-10-02 反馈的"老问题"）。
     *
     * <p>修法：建标签栏时若分组表为空，用**同一套归属函数**（`packKeyOfRecipeEntry` +
     * `packGroupFor`）把配方书当前已知集走一遍，把分组对象先创建出来 —— 与集合构建的结果完全
     * 一致（同一个 key 函数、同一个稳定分组对象），所以不会多出空标签。</p>
     */
    private static void prewarmPackGroups() {
        if (!PACK_CRAFTING_GROUPS.isEmpty()) return;
        try {
            for (net.minecraft.world.item.crafting.display.RecipeDisplayEntry entry
                    : com.alonie.brbe.cache.RecipeViewerIndex.knownEntries()) {
                if (entry == null) continue;
                for (String key : packKeysOfRecipeEntry(entry)) {
                    packGroupFor(key, null);
                }
            }
        } catch (Exception | LinkageError e) {
            BrbeLogger.log("RBIP", "pack group prewarm failed: " + e, e);
        }
    }

    /** 命名空间档的分组预热，语义/原因同 {@link #prewarmPackGroups()}：首次打开配方书时
     *  {@code updateTabs()} 早于 {@code updateCollections()}，不预热就会一个标签都不显示。 */
    private static void prewarmNamespaceGroups() {
        if (!NS_CRAFTING_GROUPS.isEmpty()) return;
        try {
            for (net.minecraft.world.item.crafting.display.RecipeDisplayEntry entry
                    : com.alonie.brbe.cache.RecipeViewerIndex.knownEntries()) {
                if (entry == null) continue;
                // 与集合端一致：命名空间档以**产物命名空间**为核心依据
                ExtendedRecipeBookCategory group = null;
                for (ItemStack stack : entry.resultItems(new net.minecraft.util.context.ContextMap.Builder().create(new net.minecraft.util.context.ContextKeySet.Builder().build()))) {
                    group = toNamespaceGroup(stack, null);
                    if (group != null) break;
                }
                if (group == null) {
                    namespaceGroupFor(namespaceKeyOfRecipe(entry.display()), null);
                }
            }
        } catch (Exception | LinkageError e) {
            BrbeLogger.log("RBIP", "namespace group prewarm failed: " + e, e);
        }
    }

    /**
     * 扩展档（命名空间 / 数据包）标签栏的底座：**只保留搜索标签**。
     */
    private static List<RecipeBookComponent.TabInfo> extendedBaseTabs(List<RecipeBookComponent.TabInfo> vanillaTabs) {
        List<RecipeBookComponent.TabInfo> tabs = new ArrayList<>();
        for (RecipeBookComponent.TabInfo info : vanillaTabs) {
            if (info.category() instanceof SearchRecipeBookCategory) {
                tabs.add(info);
                break;      // 每个配方书只有一个搜索标签
            }
        }
        return tabs;
    }

    /** 数据包模式**熔炉系**标签栏：搜索标签 + 有该类配方的包标签。 */
    public static List<RecipeBookComponent.TabInfo> withPackFurnaceTabs(
            List<RecipeBookComponent.TabInfo> vanillaTabs, FurnaceVariant type) {
        ensureInitialized();
        ensureExtendedPinKeysMigrated();
        List<RecipeBookComponent.TabInfo> tabs = extendedBaseTabs(vanillaTabs);
        Set<String> active = switch (type) {
            case SMOKER -> PACK_SMOKER_ACTIVE;
            case BLAST_FURNACE -> PACK_BLAST_ACTIVE;
            default -> PACK_FURNACE_ACTIVE;
        };
        appendPackTabs(tabs, active, type);
        return tabs;
    }

    private static void appendPackTabs(List<RecipeBookComponent.TabInfo> tabs,
                                       java.util.Collection<String> keys,
                                       FurnaceVariant variant) {
        appendExtendedTabs(tabs, keys, packGroupMap(variant), PACK_GROUP_KEY,
                packKeyOrder(keys), RecipeBookIsPain::packIconStack);
    }

    /**
     * 「扩展档」（命名空间档 / 数据包档）共用的标签追加逻辑：**pin 的排最前**（pin 顺序由
     * {@link TabPinManager} 决定），其余保持各自的自然顺序（两趟走）。
     *
     * @param groups 该档 + 该配方书类型的分组表
     * @param keyOf  分组 → 该档的键（pin 键与判定都用它）
     * @param order  已排序的键
     * @param iconFn 键 → 图标
     */
    private static void appendExtendedTabs(List<RecipeBookComponent.TabInfo> tabs,
                                           java.util.Collection<String> keys,
                                           Map<String, ExtendedRecipeBookCategory> groups,
                                           Map<ExtendedRecipeBookCategory, String> keyOf,
                                           List<String> order,
                                           java.util.function.Function<String, ItemStack> iconFn) {
        if (keys == null || keys.isEmpty()) return;
        for (int pass = 0; pass < 2; pass++) {
            for (String key : order) {
                ExtendedRecipeBookCategory group = groups.get(key);
                if (group == null) continue;
                boolean isPinned = TabPinManager.isPinnedKey(keyOf.get(group));
                if ((pass == 0) != isPinned) continue;
                ItemStack icon = iconFn.apply(key);
                if (icon == null || icon.isEmpty()) continue;
                tabs.add(new RecipeBookComponent.TabInfo(icon, Optional.empty(), group));
            }
        }
    }

    /**
     * 数据包标签的悬停提示：**优先数据包自己的声明**（{@code pack.mcmeta} 的 description；
     * 模组自带数据包其次用模组声明里的名字；联机 = 服务器名；单机未解析 = 「未知来源」）。
     * 用户规定去掉斜体、颜色改白。
     *
     * <p>**特例**：该包（= 某个模组）在创造模式物品栏里**只有一个**候选标签时，这个配方书标签
     * 事实上就是那一个创造标签 → tooltip 也改用那个创造标签的 tooltip（用户 2026-10-01）。</p>
     */
    public static Component packTabTooltip(ExtendedRecipeBookCategory category) {
        String key = PACK_GROUP_KEY.get(category);
        Component single = singleCandidateTabTooltip(key);
        return white(single != null ? single : com.alonie.brbe.cache.RecipePackIndex.label(key));
    }

    /** 统一的标签 tooltip 样式：白色、非斜体（用户 2026-10-01 定）。 */
    private static Component white(Component text) {
        return text.copy()
                .withStyle(ChatFormatting.WHITE)
                .withStyle(style -> style.withItalic(false));
    }
    // ------------------------------------------------
    //  Extended tab helpers （命名空间档 / 数据包档共用）
    // ------------------------------------------------

    /** 是不是「扩展档」（命名空间档 / 数据包档）的标签：这两档的标签都没有"代表创造标签"，
     *  图标、pin 键、tooltip 全按各自的键空间走。 */
    public static boolean isExtendedTabGroup(ExtendedRecipeBookCategory category) {
        return category != null
                && (PACK_GROUP_KEY.containsKey(category) || NS_GROUP_KEY.containsKey(category));
    }

    /** 扩展档标签的 pin 键（命名空间档 = 命名空间，数据包档 = 包 id）；不是扩展档标签返回 null。 */
    public static String extendedPinKey(ExtendedRecipeBookCategory category) {
        if (category == null) return null;
        String key = PACK_GROUP_KEY.get(category);
        return key != null ? key : NS_GROUP_KEY.get(category);
    }

    /** 该扩展档标签是不是"原版"那一档（图标固定草方块）：数据包档 = 原版包，
     *  命名空间档 = {@code minecraft}。 */
    public static boolean isVanillaTabGroup(ExtendedRecipeBookCategory category) {
        return isVanillaPackGroup(category) || isVanillaNamespaceGroup(category);
    }

    /** 扩展档标签的悬停提示：数据包档 = 数据包名字，命名空间档 = 模组名 / 命名空间。 */
    public static Component extendedTabTooltip(ExtendedRecipeBookCategory category) {
        if (category != null && NS_GROUP_KEY.containsKey(category)) return namespaceTabTooltip(category);
        return packTabTooltip(category);
    }

    // ------------------------------------------------
    //  Namespace tabs （命名空间标签：配方 id 的命名空间 → 一个标签）
    // ------------------------------------------------

    /**
     * 命名空间标签的注册表：**每个命名空间一个分类对象**，按需创建、**身份稳定**
     * （{@code collectionsByTab} 的键按身份比较，不能每次重建都 new 一个）。
     *
     * <p>归组键 = {@link com.alonie.brbe.cache.RecipeNamespaceIndex} 反查出的**配方 id 的命名空间**
     * （模组用自己的命名空间、纯数据包配方用自己的命名空间）。反查不到时退回**产物物品的命名空间**；
     * 产物也解析不出来 → {@link #NS_UNKNOWN_KEY}「未知来源」标签。</p>
     *
     * <p>与数据包档那套**并行且互不干扰**：同一个字符串在两档里含义不同（模组的命名空间与
     * 模组自带数据包 id 常常同名），所以分类表与 pin 键空间各有一套。</p>
     */
    private static final Map<String, ExtendedRecipeBookCategory> NS_CRAFTING_GROUPS = new LinkedHashMap<>();
    private static final Map<String, ExtendedRecipeBookCategory> NS_FURNACE_GROUPS = new LinkedHashMap<>();
    private static final Map<String, ExtendedRecipeBookCategory> NS_SMOKER_GROUPS = new LinkedHashMap<>();
    private static final Map<String, ExtendedRecipeBookCategory> NS_BLAST_GROUPS = new LinkedHashMap<>();
    /** 命名空间分类 → 命名空间（四套共用）。 */
    private static final Map<ExtendedRecipeBookCategory, String> NS_GROUP_KEY = new HashMap<>();
    /** 该熔炉类型下**已经有配方**的命名空间（供 {@code withNamespaceFurnaceTabs} 过滤空标签）。 */
    public static final Set<String> NS_FURNACE_ACTIVE = new HashSet<>();
    public static final Set<String> NS_SMOKER_ACTIVE = new HashSet<>();
    public static final Set<String> NS_BLAST_ACTIVE = new HashSet<>();
    /** 命名空间 → 该标签下出现过的产物物品（**图标随机池**；按书里的顺序收集，封顶
     *  {@link #ICON_POOL_CAP} 条）。 */
    private static final Map<String, List<ItemStack>> nsIconPool = new HashMap<>();
    /** 命名空间 → 兜底图标（该命名空间下第一个注册物品），池空时才用；每次会话只扫一次注册表。 */
    private static final Map<String, ItemStack> nsFallbackIcons = new HashMap<>();
    /** 归组不到任何命名空间时的键（真实命名空间不会以 {@code #} 开头）。 */
    public static final String NS_UNKNOWN_KEY = "#unknown";

    private static Map<String, ExtendedRecipeBookCategory> nsGroupMap(FurnaceVariant variant) {
        if (variant == null) return NS_CRAFTING_GROUPS;
        return switch (variant) {
            case SMOKER -> NS_SMOKER_GROUPS;
            case BLAST_FURNACE -> NS_BLAST_GROUPS;
            default -> NS_FURNACE_GROUPS;
        };
    }

    private static synchronized ExtendedRecipeBookCategory nsGroup(String key, FurnaceVariant variant) {
        Map<String, ExtendedRecipeBookCategory> map = nsGroupMap(variant);
        ExtendedRecipeBookCategory group = map.get(key);
        if (group == null) {
            group = new RecipeBookCategory();
            map.put(key, group);
            NS_GROUP_KEY.put(group, key);
        }
        return group;
    }

    /**
     * 归组键（命名空间档**回退路径**，2026-10-02 起）：**配方 id 的命名空间**。
     *
     * <p>命名空间档的核心依据已改为**产物的命名空间**
     * （{@link #toNamespaceGroup(ItemStack, FurnaceVariant)}，见 {@code ClientRecipeBookMixin}）；
     * 本方法只在产物解析不出来（特殊配方 / 无产物显示）时兜底。反查不到配方 id 时返回 {@code null}，
     * 调用方再退回「未知来源」标签。</p>
     */
    public static String namespaceKeyOfRecipe(net.minecraft.world.item.crafting.display.RecipeDisplay display) {
        return com.alonie.brbe.cache.RecipeNamespaceIndex.namespaceOf(display);
    }

    /** 按**命名空间键**取（或建）标签分类 —— 键就是命名空间本身（或 {@link #NS_UNKNOWN_KEY}）。 */
    public static synchronized ExtendedRecipeBookCategory namespaceGroupFor(String key, FurnaceVariant variant) {
        if (key == null) return null;
        return nsGroup(key, variant);
    }

    /** **主路径**（用户 2026-10-02 定）：按**产物物品的命名空间**归组 —— 命名空间档的核心依据。 */
    public static ExtendedRecipeBookCategory toNamespaceGroup(ItemStack stack, FurnaceVariant variant) {
        if (stack == null || stack.isEmpty()) return null;
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) return null;
        return namespaceGroupFor(id.getNamespace(), variant);
    }

    /**
     * 记下该标签下出现过的产物物品（图标随机池）。
     *
     * <p>图标规则（用户 2026-10-01 定）：**{@code minecraft} 命名空间保留草方块特例**，其它命名空间
     * 从池里**稳定伪随机**挑一个 —— 种子 = 命名空间 + 世界盐
     * （{@link com.alonie.brbe.util.WorldScopedStore#salt()}），同一存档内不变。</p>
     */
    public static synchronized void rememberNamespaceProduct(String key, ItemStack stack) {
        if (key == null || stack == null || stack.isEmpty()) return;
        List<ItemStack> pool = nsIconPool.computeIfAbsent(key, k -> new ArrayList<>());
        if (pool.size() >= ICON_POOL_CAP) return;
        pool.add(stack.copyWithCount(1));
    }

    /** 该分类是不是命名空间档标签。 */
    public static boolean isNamespaceGroup(ExtendedRecipeBookCategory category) {
        return category != null && NS_GROUP_KEY.containsKey(category);
    }

    /** 该分类是不是命名空间档的**原版**标签（{@code minecraft}）—— 图标固定草方块。 */
    public static boolean isVanillaNamespaceGroup(ExtendedRecipeBookCategory category) {
        return isVanillaNamespaceKey(NS_GROUP_KEY.get(category));
    }

    private static boolean isVanillaNamespaceKey(String key) {
        return "minecraft".equals(key);
    }

    /** 命名空间档标签的命名空间（或 {@code #unknown}）；不是命名空间档标签返回 null。 */
    public static String namespaceKey(ExtendedRecipeBookCategory category) {
        return NS_GROUP_KEY.get(category);
    }

    /** 命名空间档标签的 **pin 键** = 命名空间本身（或 {@code #unknown}）。 */
    public static String namespacePinKey(ExtendedRecipeBookCategory category) {
        return NS_GROUP_KEY.get(category);
    }

    /**
     * 标签图标（用户 2026-10-01 定的规则）：
     *
     * <ol>
     *   <li>**{@code minecraft} = 草方块**（保留的特例）；</li>
     *   <li>该命名空间（模组）在创造模式物品栏里**序号最靠前**的标签的图标；</li>
     *   <li>该命名空间在原版物品栏里**归于原版标签**、或根本没有创造标签 → 在该命名空间的
     *       产物池里稳定伪随机挑一个；池空 → 该命名空间下**第一个注册物品**；再没有 → 知识之书
     *       （标签不会凭空消失）。</li>
     * </ol>
     */
    private static ItemStack nsIconStack(String namespace) {
        if (isVanillaNamespaceKey(namespace)) return new ItemStack(Items.GRASS_BLOCK);
        ItemStack tabIcon = sourceIconStack(namespace);
        if (!tabIcon.isEmpty()) return tabIcon;
        List<ItemStack> pool = nsIconPool.get(namespace);
        if (pool != null && !pool.isEmpty()) {
            int index = Math.floorMod(namespace.hashCode() * 31
                    + com.alonie.brbe.util.WorldScopedStore.salt(), pool.size());
            return pool.get(index).copy();
        }
        ItemStack fallback = nsFallbackIcon(namespace);
        return fallback.isEmpty() ? new ItemStack(Items.KNOWLEDGE_BOOK) : fallback;
    }

    /** 该命名空间下第一个注册物品（缓存；扫不到缓存空物品，避免反复遍历注册表）。 */
    private static synchronized ItemStack nsFallbackIcon(String namespace) {
        ItemStack cached = nsFallbackIcons.get(namespace);
        if (cached != null) return cached;
        ItemStack found = ItemStack.EMPTY;
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id != null && namespace.equals(id.getNamespace())) {
                found = new ItemStack(item);
                break;
            }
        }
        nsFallbackIcons.put(namespace, found);
        return found;
    }

    /**
     * 命名空间档**合成台**标签栏：**搜索标签 + 每个命名空间一个标签**。
     *
     * <p>原版配方书标签（建筑方块 / 红石 / 装备 / 杂项）不再出现 —— 原版配方自己的命名空间
     * {@code minecraft} 就是其中一个标签（图标草方块）。固定（pin）过的标签排到标签块最前面。</p>
     */
    public static List<RecipeBookComponent.TabInfo> withNamespaceTabs(List<RecipeBookComponent.TabInfo> vanillaTabs) {
        ensureInitialized();
        ensureExtendedPinKeysMigrated();
        prewarmNamespaceGroups();
        List<RecipeBookComponent.TabInfo> tabs = extendedBaseTabs(vanillaTabs);
        appendNamespaceTabs(tabs, NS_CRAFTING_GROUPS.keySet(), null);
        return tabs;
    }

    /** 命名空间档**熔炉系**标签栏：搜索标签 + 有该类配方的命名空间标签。 */
    public static List<RecipeBookComponent.TabInfo> withNamespaceFurnaceTabs(
            List<RecipeBookComponent.TabInfo> vanillaTabs, FurnaceVariant type) {
        ensureInitialized();
        ensureExtendedPinKeysMigrated();
        List<RecipeBookComponent.TabInfo> tabs = extendedBaseTabs(vanillaTabs);
        Set<String> active = switch (type) {
            case SMOKER -> NS_SMOKER_ACTIVE;
            case BLAST_FURNACE -> NS_BLAST_ACTIVE;
            default -> NS_FURNACE_ACTIVE;
        };
        appendNamespaceTabs(tabs, active, type);
        return tabs;
    }

    private static void appendNamespaceTabs(List<RecipeBookComponent.TabInfo> tabs,
                                            java.util.Collection<String> keys,
                                            FurnaceVariant variant) {
        appendExtendedTabs(tabs, keys, nsGroupMap(variant), NS_GROUP_KEY,
                namespaceKeyOrder(keys), RecipeBookIsPain::nsIconStack);
    }

    /**
     * 命名空间标签的悬停提示（用户 2026-10-01 定）：**直接取 mod 声明里的模组名**
     * （{@code fabric.mod.json} 的 name，模组菜单里显示的那个）；没有该模组时退回
     * {@code ModNameUtil} 的 jade / 命名空间兜底。归组不到命名空间时为「未知来源」。
     *
     * <p>**特例**：该模组在创造模式物品栏里**只有一个**候选标签时，tooltip 用那个创造标签的
     * tooltip（它与配方书标签是同一个东西）。</p>
     */
    public static Component namespaceTabTooltip(ExtendedRecipeBookCategory category) {
        String namespace = NS_GROUP_KEY.get(category);
        if (namespace == null || namespace.isBlank() || NS_UNKNOWN_KEY.equals(namespace)) {
            return white(Component.translatable("brbe.tab.namespace.unknown"));
        }
        Component single = singleCandidateTabTooltip(namespace);
        if (single != null) return white(single);
        return white(Component.literal(ModNameUtil.resolveModDisplayName(namespace)));
    }

    /**
     * 标签排列顺序（用户 2026-10-01）：**原版标签（{@code minecraft}）永远排最前**；有创造标签的
     * 模组按它**序号最靠前的创造标签**排；纯数据包命名空间排在其后（按名字典序）；
     * 「未知来源」永远最后。
     */
    private static List<String> namespaceKeyOrder(java.util.Collection<String> keys) {
        List<String> order = new ArrayList<>(keys);
        order.sort((a, b) -> {
            int cmp = Integer.compare(namespaceSortRank(a), namespaceSortRank(b));
            if (cmp != 0) return cmp;
            boolean aUnknown = isUnknownKey(a);
            boolean bUnknown = isUnknownKey(b);
            if (aUnknown != bUnknown) return aUnknown ? 1 : -1;
            return a.compareTo(b);
        });
        return order;
    }

    private static int namespaceSortRank(String namespace) {
        if (namespace == null) return Integer.MAX_VALUE;
        if (isVanillaNamespaceKey(namespace)) return -1;        // 原版标签的序号必须最靠前
        if (isUnknownKey(namespace)) return Integer.MAX_VALUE;  // 「未知来源」最后
        int rank = Integer.MAX_VALUE;
        for (CreativeModeTab tab : sourceCreativeTabs(namespace)) {
            int index = MIRRORED_ITEM_GROUPS.indexOf(tab);
            if (index >= 0) rank = Math.min(rank, index);
        }
        return rank;
    }

    // ------------------------------------------------
    //  Pin key migration （三档 pin 键空间，一次性）
    // ------------------------------------------------

    /** pin 键迁移只跑一次（或"这次没迁成"→ 数据未就绪时留到下次）。 */
    private static boolean extendedPinKeysMigrated;

    /**
     * pin 键一次性迁移（2026-10-01 三档拆分）：把旧版第二档的 pin 列表
     * （{@code brbe.tabpins.json} 的 {@code compactTabs}，历史上先后存过"代表创造标签 id /
     * 裸命名空间"与"数据包 id"两种键）**按内容分流**：
     *
     * <ul>
     *   <li>数据包 id 形态（{@code vanilla} / {@code #unknown} / 含 {@code /} / {@code .zip}，
     *       或包索引认识它）→ **数据包档**键空间；</li>
     *   <li>其余 → **命名空间档**键空间，形如 {@code ns:path} 的旧创造标签 id 取冒号前的命名空间；</li>
     *   <li>映射不到（例如命名空间档收到了包 id）→ 丢弃并记一行日志。</li>
     * </ul>
     *
     * <p>联机时包索引不可用（{@link com.alonie.brbe.cache.RecipePackIndex#available()} = false），
     * 分类只能按形态判断 —— 因此迁移推迟到"索引可用"的场合（单机进世界后首次打开配方书）。</p>
     */
    private static synchronized void ensureExtendedPinKeysMigrated() {
        if (extendedPinKeysMigrated) return;
        if (!com.alonie.brbe.cache.RecipePackIndex.available()) return;
        extendedPinKeysMigrated = true;
        try {
            int changed = TabPinManager.resolveLegacyCompact(
                    RecipeBookIsPain::looksLikeDatapackId,
                    RecipeBookIsPain::toNamespacePinKey,
                    RecipeBookIsPain::toDatapackPinKey);
            if (changed > 0) {
                BrbeLogger.log("RBIP", "tab pins: migrated/split {} legacy keys (namespace / datapack)", changed);
            }
        } catch (Exception | LinkageError e) {
            BrbeLogger.log("RBIP", "tab pin migration failed: {}", e.toString());
        }
    }

    /** 旧键是不是**数据包 id**：包索引可用时精确判定，否则按形态（{@code vanilla} /
     *  {@code #unknown} / 含 {@code /} / 以 {@code .zip} 结尾）。 */
    private static boolean looksLikeDatapackId(String key) {
        if (key == null || key.isBlank()) return false;
        if (com.alonie.brbe.cache.RecipePackIndex.UNKNOWN_KEY.equals(key)) return true;
        if (key.indexOf('/') >= 0 || key.endsWith(".zip")) return true;
        if ("vanilla".equals(key)) return true;
        return com.alonie.brbe.cache.RecipePackIndex.isPackKey(key)
                && !isKnownNamespace(key);
    }

    /** 该字符串是不是当前已知的命名空间（包索引里出现过该命名空间的配方）。 */
    private static boolean isKnownNamespace(String key) {
        return com.alonie.brbe.cache.RecipePackIndex.namespaceOwner(key) != null;
    }

    /** 旧键 → **命名空间档** pin 键：创造标签 id 取冒号前的命名空间；包 id / 未知 → 丢弃。 */
    private static String toNamespacePinKey(String key) {
        if (key == null || key.isBlank()) return null;
        if (com.alonie.brbe.cache.RecipePackIndex.UNKNOWN_KEY.equals(key)) return null;
        String namespace = key.indexOf(':') >= 0 ? key.substring(0, key.indexOf(':')) : key;
        if (namespace.isBlank()) return null;
        // 明确的包 id（形如 mod/xxx、file/xxx.zip）在命名空间档里没有意义 → 丢弃
        if (looksLikeDatapackId(key) && !namespace.equals(key)) return null;
        if (looksLikeDatapackId(key) && !isKnownNamespace(namespace)) return null;
        return namespace;
    }

    /** 旧键 → **数据包档** pin 键：包 id 原样保留；命名空间 / 创造标签 id 经
     *  {@code namespaceOwner} 映射到提供它的包；映射不到丢弃。 */
    private static String toDatapackPinKey(String key) {
        if (key == null || key.isBlank()) return null;
        if (com.alonie.brbe.cache.RecipePackIndex.UNKNOWN_KEY.equals(key)) return key;
        if (looksLikeDatapackId(key) && !isKnownNamespace(key)) return key;
        if ("minecraft".equals(key)) return "vanilla";
        String namespace = key.indexOf(':') >= 0 ? key.substring(0, key.indexOf(':')) : key;
        return com.alonie.brbe.cache.RecipePackIndex.namespaceOwner(namespace);
    }
    // ------------------------------------------------
    //  Furnace type detection
    // ------------------------------------------------
    public static FurnaceVariant detectFurnaceType(List<RecipeBookComponent.TabInfo> tabs) {
        for (RecipeBookComponent.TabInfo info : tabs) {
            ExtendedRecipeBookCategory cat = info.category();
            if (cat == SearchRecipeBookCategory.SMOKER) return FurnaceVariant.SMOKER;
            if (cat == SearchRecipeBookCategory.BLAST_FURNACE) return FurnaceVariant.BLAST_FURNACE;
        }
        return FurnaceVariant.FURNACE;
    }

    // ------------------------------------------------
    //  Furnace creative tab support
    // ------------------------------------------------

    public static List<RecipeBookComponent.TabInfo> withFurnaceCreativeTabs(
            List<RecipeBookComponent.TabInfo> originalTabs, FurnaceVariant type) {
        return switch (type) {
            case SMOKER -> withFurnaceCreativeTabs(originalTabs, SMOKER_BOOK_GROUP_TO_ITEM_GROUP, SMOKER_ACTIVE_TABS);
            case BLAST_FURNACE -> withFurnaceCreativeTabs(originalTabs, BLAST_FURNACE_BOOK_GROUP_TO_ITEM_GROUP, BLAST_FURNACE_ACTIVE_TABS);
            default -> withFurnaceCreativeTabs(originalTabs, FURNACE_BOOK_GROUP_TO_ITEM_GROUP, FURNACE_ACTIVE_TABS);
        };
    }

    private static List<RecipeBookComponent.TabInfo> withFurnaceCreativeTabs(
            List<RecipeBookComponent.TabInfo> originalTabs,
            BiMap<ExtendedRecipeBookCategory, CreativeModeTab> groupMap,
            Set<CreativeModeTab> activeTabs) {
        ensureInitialized();
        List<RecipeBookComponent.TabInfo> expandedTabs = new ArrayList<>();

        // Keep the original furnace-specific search tab
        originalTabs.stream()
                .filter(tab -> tab.category() instanceof SearchRecipeBookCategory)
                .findFirst()
                .ifPresent(expandedTabs::add);

        // If no search tab found in original, add a default furnace one
        if (expandedTabs.isEmpty()) {
            expandedTabs.add(new RecipeBookComponent.TabInfo(SearchRecipeBookCategory.FURNACE));
        }

        // Always filter by active tabs — only show tabs that have recipes.
        // The paginateTabButtons hook also enforces this as a safety net.
        // 固定的创造标签排在最前（搜索标签之下，按 pin 顺序），其余按自然顺序。
        List<CreativeModeTab> pinned = TabPinManager.pinnedTabs();
        for (CreativeModeTab tab : pinned) {
            if (!activeTabs.contains(tab) || !MIRRORED_ITEM_GROUPS.contains(tab)) continue;
            ExtendedRecipeBookCategory group = groupMap.inverse().get(tab);
            if (group != null) {
                expandedTabs.add(new RecipeBookComponent.TabInfo(tab.getIconItem(), Optional.empty(), group));
            }
        }
        for (CreativeModeTab tab : MIRRORED_ITEM_GROUPS) {
            if (pinned.contains(tab)) continue;
            if (!activeTabs.contains(tab)) continue;
            ExtendedRecipeBookCategory group = groupMap.inverse().get(tab);
            if (group != null) {
                expandedTabs.add(new RecipeBookComponent.TabInfo(tab.getIconItem(), Optional.empty(), group));
            }
        }
        return expandedTabs;
    }

    /** 创造模式档（熔炉系）：物品对应的第一个配方书分类；多值见
     *  {@link #toFurnaceRecipeBookGroups(ItemStack, FurnaceVariant)}。 */
    public static ExtendedRecipeBookCategory toFurnaceRecipeBookGroup(ItemStack stack, FurnaceVariant type) {
        List<ExtendedRecipeBookCategory> groups = toFurnaceRecipeBookGroups(stack, type);
        return groups.isEmpty() ? null : groups.get(0);
    }

    // ------------------------------------------------
    //  Icon rendering — owo-lib animated icons
    // ------------------------------------------------

    public static boolean rbip$renderOwo(GuiGraphics context, int i, RecipeBookTabButton widget, CreativeModeTab group) {
        return rbip$renderOwo(context, widget.getX() + 9 + i, widget.getY() + 5, group);
    }

    public static boolean rbip$renderOwo(GuiGraphics context, int x, int y, CreativeModeTab group) {
        // owo-lib not bundled — use standard icon rendering
        return false;
    }

}

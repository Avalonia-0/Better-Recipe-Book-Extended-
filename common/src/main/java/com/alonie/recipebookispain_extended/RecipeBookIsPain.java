package com.alonie.recipebookispain_extended;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.config.AppContext;
import com.alonie.brbe.config.ConfigEventBus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RecipeBookIsPain {

    public static final Logger LOGGER = LogManager.getLogger("RBIP");
    public static PlatformAbstractions PLATFORM;
    public static boolean isOwOLoaded;

    /** Creative tabs that should appear in the recipe book. */
    public static final List<CreativeModeTab> CRAFTING_LIST = new ArrayList<>();
    /** Same tabs, used for search context. */
    public static final List<CreativeModeTab> CRAFTING_SEARCH_LIST = new ArrayList<>();

    private static boolean initialized;
    private static boolean initAttempted;

    /** Incremented when tab→item mappings are rebuilt. Used by cache consumers. */
    public static int recipeGeneration;

    /** The currently-selected creative tab (set by RecipeBookWidgetMixin). */
    public static CreativeModeTab activeCreativeTab;

    /** Which furnace variant is active (set by RecipeBookWidgetMixin for furnace screens). */
    public static FurnaceVariant activeFurnaceType;

    /** Detect furnace variant from the container menu. */
    public static FurnaceVariant detectFurnaceType(AbstractFurnaceMenu menu) {
        if (menu instanceof SmokerMenu) return FurnaceVariant.SMOKER;
        if (menu instanceof BlastFurnaceMenu) return FurnaceVariant.BLAST_FURNACE;
        return FurnaceVariant.FURNACE;
    }

    // ── Scroll-queue (updated by RbipMouseScrollMixin) ─────────

    /** Pending scroll direction: >0 = up, <0 = down, 0 = none. */
    private static int rbip$pendingScroll;

    /** Consume and reset the pending scroll. */
    public static int rbip$consumeScroll() {
        int s = rbip$pendingScroll;
        rbip$pendingScroll = 0;
        return s;
    }

    /** Queue a scroll event (called from MouseHandler.onScroll mixin). */
    public static void rbip$queueScroll(int direction) {
        rbip$pendingScroll = direction;
    }

    // ── Item-to-tab lookup cache ────────────────────────────────────
    // Map from CreativeModeTab → set of Items that belong to that tab
    private static final Map<CreativeModeTab, Set<Item>> TAB_ITEMS = new HashMap<>();
    // Reverse: Item → CreativeModeTab (first match wins)
    private static final Map<Item, CreativeModeTab> ITEM_TO_TAB = new HashMap<>();

    /**
     * 物品→额外标签页的覆盖映射（手动安全网）。
     * 某些物品在原版创造标签页中的归属与配方书分类预期不符。
     * 此映射将这些物品额外关联到正确的标签页，使其在对应的 RBIP 标签页下可见。
     * 这些是独立确定的修正，不受其他机制影响。
     *
     * Key: 物品ID (Registry key), Value: 目标创造标签页的注册路径 (如 "redstone_blocks")
     */
    private static final Map<ResourceLocation, ResourceLocation> TAB_OVERRIDES = new HashMap<>();

    static {
        // 红石火把：原版归在 functional_blocks，但作为红石元件应同时在 redstone_blocks 可见
        TAB_OVERRIDES.put(
                ResourceLocation.parse("minecraft:redstone_torch"),
                ResourceLocation.parse("redstone_blocks"));
        TAB_OVERRIDES.put(
                ResourceLocation.parse("minecraft:redstone_wall_torch"),
                ResourceLocation.parse("redstone_blocks"));
    }

    private static final Map<String, CreativeModeTab> namespaceCache = new HashMap<>();
    private static boolean namespaceCacheBuilt;

    // ── Diagnostic ─────────────────────────────────────────────────

    public static String diagnostic() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n══════ RBIP Diagnostic ══════\n");
        sb.append("  PLATFORM         = ").append(PLATFORM).append("\n");
        sb.append("  isOwOLoaded      = ").append(isOwOLoaded).append("\n");
        sb.append("  initialized      = ").append(initialized).append("\n");
        sb.append("  initAttempted    = ").append(initAttempted).append("\n");
        sb.append("  config           = ").append(BetterRecipeBook.ctx().config()).append("\n");
        if (BetterRecipeBook.ctx().config() != null && BetterRecipeBook.ctx().config().rbip != null) {
            sb.append("  config.rbip.enableRecipeBookIsPain = ")
              .append(BetterRecipeBook.ctx().config().rbip.enableRecipeBookIsPain).append("\n");
        }
        sb.append("  enabled()        = ").append(RecipeBookIsPainExtendedConfig.enabled()).append("\n");
        sb.append("  CRAFTING_LIST    = ").append(CRAFTING_LIST.size()).append(" entries\n");
        for (CreativeModeTab tab : CRAFTING_LIST) {
            sb.append("    - ").append(tab.getDisplayName().getString())
              .append(" (").append(TAB_ITEMS.getOrDefault(tab, Set.of()).size()).append(" items)\n");
        }
        sb.append("  CreativeModeTabs = ").append(CreativeModeTabs.allTabs().size()).append(" tabs\n");
        sb.append("  ITEM_TO_TAB      = ").append(ITEM_TO_TAB.size()).append(" entries\n");
        sb.append("══════════════════════════════\n");
        return sb.toString();
    }

    // ── Initialization ────────────────────────────────────────────

    public static synchronized void ensureInitialized() {
        LOGGER.info("[RBIP] ensureInitialized() — init={}, attempted={}, enabled={}",
                initialized, initAttempted, RecipeBookIsPainExtendedConfig.enabled());

        if (!RecipeBookIsPainExtendedConfig.enabled()) {
            LOGGER.info("[RBIP] DISABLED — skipping init");
            if (initialized) {
                initialized = false;
                recipeGeneration++;
                CRAFTING_LIST.clear();
                CRAFTING_SEARCH_LIST.clear();
                TAB_ITEMS.clear();
                ITEM_TO_TAB.clear();
            }
            initAttempted = true;
            return;
        }

        if (initialized) return;

        Minecraft client = Minecraft.getInstance();
        if (client == null) { LOGGER.warn("[RBIP] client is null"); return; }
        if (client.level == null) { LOGGER.info("[RBIP] no level yet — defer"); return; }

        LOGGER.info("[RBIP] Initializing...");

        try {
            CRAFTING_LIST.clear();
            CRAFTING_SEARCH_LIST.clear();
            TAB_ITEMS.clear();
            ITEM_TO_TAB.clear();

            // Force creative tabs to rebuild their display contents.
            // On Fabric, getDisplayItems() can return empty until the
            // creative inventory has been opened at least once.  Calling
            // tryRebuildTabContents() forces population regardless of
            // whether the creative screen has ever been opened, which is
            // necessary when a recipe viewer (JEI/REI/EMI) is also
            // hooking into creative tab population.
            CreativeModeTabs.tryRebuildTabContents(
                    FeatureFlags.DEFAULT_FLAGS,
                    false,
                    client.level.registryAccess()
            );

            // ── Strategy A: use getDisplayItems() (vanilla + NeoForge) ──
            int tabCount = 0;
            for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
                if (!shouldMirror(tab)) continue;
                try {
                    Set<Item> items = new HashSet<>();
                    for (ItemStack stack : tab.getDisplayItems()) {
                        if (!stack.isEmpty()) {
                            Item item = stack.getItem();
                            items.add(item);
                            ITEM_TO_TAB.putIfAbsent(item, tab);
                        }
                    }
                    if (!items.isEmpty()) {
                        TAB_ITEMS.put(tab, items);
                        CRAFTING_LIST.add(tab);
                        CRAFTING_SEARCH_LIST.add(tab);
                        tabCount++;
                    }
                } catch (Exception e) {
                    LOGGER.error("[RBIP] Error processing tab: {}", tab.getDisplayName().getString(), e);
                }
            }

            // ── Strategy B: fallback — scan BuiltInRegistries.ITEM ──
            // On Fabric getDisplayItems() can return empty when the creative
            // inventory has not been opened yet.  NeoForge does not have this
            // issue because it uses Mojang names directly.
            if (tabCount == 0) {
                LOGGER.info("[RBIP] getDisplayItems() returned empty — falling back to BuiltInRegistries.ITEM scan");
                for (Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                    ItemStack stack = new ItemStack(item);
                    if (stack.isEmpty()) continue;
                    for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
                        if (!shouldMirror(tab)) continue;
                        try {
                            if (tab.contains(stack)) {
                                ITEM_TO_TAB.putIfAbsent(item, tab);
                                TAB_ITEMS.computeIfAbsent(tab, k -> new HashSet<>()).add(item);
                                break;
                            }
                        } catch (Exception ignored) {}
                    }
                }
                // Build CRAFTING_LIST from the TAB_ITEMS map
                for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
                    if (!shouldMirror(tab)) continue;
                    Set<Item> items = TAB_ITEMS.get(tab);
                    if (items != null && !items.isEmpty()) {
                        CRAFTING_LIST.add(tab);
                        CRAFTING_SEARCH_LIST.add(tab);
                    }
                }
            }

            recipeGeneration++;

            // If we mapped zero items, this is likely a race with JEI/REI
            // creative-tab population.  Allow exactly one retry by deferring
            // the "initialized" flag until the next ensureInitialized() call.
            if (CRAFTING_LIST.isEmpty()) {
                if (!initAttempted) {
                    // First attempt — keep initialized=false, mark attempted for one retry
                    LOGGER.warn("[RBIP] 0 tabs mapped (likely recipe-viewer init race) — will retry");
                    initAttempted = true;
                } else {
                    // Already retried — give up to prevent infinite retries
                    LOGGER.warn("[RBIP] 0 tabs mapped on retry — giving up");
                    initialized = true;
                }
            } else {
                initialized = true;
                initAttempted = true;
            }

            // ── 应用物品→标签页覆盖 ─────────────────────────────────
            // 对已知分类有误的物品，补充其到正确的标签页关联。
            // 这确保红石火把等物品也能在红石方块标签页中显示。
            applyTabOverrides();

            LOGGER.info("[RBIP] OK — {} tabs, {} items mapped (strategy: {})",
                    CRAFTING_LIST.size(), ITEM_TO_TAB.size(),
                    tabCount > 0 ? "getDisplayItems" : "registry scan");
        } catch (Exception e) {
            LOGGER.error("[RBIP] Init failed", e);
        }
    }

    private static boolean shouldMirror(CreativeModeTab tab) {
        // Skip inventory, hotbar, and search tabs
        CreativeModeTab.Type type = tab.getType();
        return type != CreativeModeTab.Type.INVENTORY
                && type != CreativeModeTab.Type.SEARCH;
    }

    /**
     * Register RBIP as a config-change subscriber on the BRBE event bus.
     * Called once from {@code BetterRecipeBook.init()} — replaces the old
     * hardcoded {@code RecipeBookIsPain.onConfigChanged()} call.
     */
    public static void init(ConfigEventBus events) {
        events.subscribe(ConfigEventBus.ConfigChanged.class, event -> {
            LOGGER.info("[RBIP] ConfigChanged event received");
            onConfigChanged();
        });
        LOGGER.info("[RBIP] Subscribed to ConfigEventBus");
    }

    public static void onConfigChanged() {
        LOGGER.info("[RBIP] onConfigChanged()");
        initialized = false;
        initAttempted = false;
        recipeGeneration++;
        CRAFTING_LIST.clear();
        CRAFTING_SEARCH_LIST.clear();
        TAB_ITEMS.clear();
        ITEM_TO_TAB.clear();
        namespaceCacheBuilt = false;
        namespaceCache.clear();
        if (RecipeBookIsPainExtendedConfig.enabled()) ensureInitialized();
    }

    // ── Creative tab → item lookup ────────────────────────────────

    /** Get all items in a creative tab. */
    public static Set<Item> getItemsForTab(CreativeModeTab tab) {
        return TAB_ITEMS.getOrDefault(tab, Set.of());
    }

    /** Find which creative tab an item belongs to. */
    public static CreativeModeTab getCreativeTabForItem(ItemStack stack) {
        if (stack.isEmpty()) return null;
        return ITEM_TO_TAB.get(stack.getItem());
    }

    /** Check if a recipe result item belongs to a given creative tab. */
    public static boolean isItemInTab(ItemStack stack, CreativeModeTab tab) {
        Set<Item> items = TAB_ITEMS.get(tab);
        return items != null && items.contains(stack.getItem());
    }

    /**
     * 应用物品→创造标签页覆盖。
     * 一些物品在原版 Minecraft 中被分配到与其功能不匹配的创造标签页。
     * 例如红石火把被放在"功能方块"标签页，但配方书用户期望在"红石方块"标签页中看到它。
     * 此方法将 {@link #TAB_OVERRIDES} 中定义的覆盖应用到 {@link #TAB_ITEMS} 和 {@link #ITEM_TO_TAB}。
     *
     * 设计原则：
     * - 只在 TAB_ITEMS 中添加（不替换）条目，使物品在多个标签页中都可显示
     * - {@link #ITEM_TO_TAB.putIfAbsent} 保留首个标签页作为主映射
     * - 覆盖仅影响 RBIP 配方标签页筛选，不影响原版创造物品栏
     */
    /**
     * 应用物品→创造标签页覆盖（移动模式）。
     * <p>
     * 与 {@link #applyCategoryCrossReference} 的追加模式不同，此方法对特定物品执行"移动"：
     * 将物品从所有其他标签页中移除，只保留在目标标签页中。
     * 这用于处理原版分类明显错误的个别物品（如红石火把在功能方块而非红石方块）。
     * <p>
     * 设计原则：
     * - 从所有标签页的 TAB_ITEMS 中移除该物品（防止重复出现）
     * - 然后单独添加到目标标签页
     * - 更新 ITEM_TO_TAB 主映射
     */
    private static void applyTabOverrides() {
        for (Map.Entry<ResourceLocation, ResourceLocation> entry : TAB_OVERRIDES.entrySet()) {
            // 查找目标创造标签页（由注册路径定位，跨版本兼容）
            ResourceLocation tabId = entry.getValue();
            CreativeModeTab targetTab = null;
            for (CreativeModeTab candidate : CreativeModeTabs.allTabs()) {
                ResourceLocation candidateId = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(candidate);
                if (candidateId != null && candidateId.getPath().equals(tabId.getPath())) {
                    targetTab = candidate;
                    break;
                }
            }
            if (targetTab == null) {
                LOGGER.warn("[RBIP] Tab override target '{}' not found", tabId);
                continue;
            }

            // 查找物品
            Item item = BuiltInRegistries.ITEM.get(entry.getKey());
            if (item == net.minecraft.world.level.block.Blocks.AIR.asItem()) {
                LOGGER.warn("[RBIP] Tab override item '{}' not found in registry", entry.getKey());
                continue;
            }

            // 从所有其他标签页中移除该物品，防止重复出现
            // Remove from ALL other tabs first — this is a "move", not an "add".
            // The item should only appear in the target tab, not in its original
            // vanilla assignment (e.g. redstone_torch removed from functional_blocks).
            for (Set<Item> items : TAB_ITEMS.values()) {
                items.remove(item);
            }

            // 添加到目标标签页
            TAB_ITEMS.computeIfAbsent(targetTab, k -> new HashSet<>()).add(item);
            // 覆盖主映射
            ITEM_TO_TAB.put(item, targetTab);

            LOGGER.info("[RBIP] Tab override: moved '{}' exclusively to '{}'",
                    entry.getKey(), tabId.getPath());
        }
    }

    // ── Namespace cache ────────────────────────────────────────────

    public static void buildNamespaceCache() {
        namespaceCache.clear();
        for (CreativeModeTab group : CreativeModeTabs.allTabs()) {
            CreativeModeTab.Type type = group.getType();
            if (type == CreativeModeTab.Type.INVENTORY
                    || type == CreativeModeTab.Type.SEARCH) {
                continue;
            }
            ResourceLocation regId = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(group);
            if (regId != null) {
                namespaceCache.put(regId.getPath(), group);
            }
            String displayKey = group.getDisplayName().getString().toLowerCase()
                    .replaceAll("[^a-z0-9_]", "");
            if (!displayKey.isEmpty()) {
                namespaceCache.put(displayKey, group);
            }
        }
        namespaceCacheBuilt = true;
    }

    public static CreativeModeTab lookupByNamespace(String itemNamespace) {
        if (!namespaceCacheBuilt) buildNamespaceCache();
        return namespaceCache.get(itemNamespace);
    }

    public static void applyNamespaceOverrides() {
        // Namespace overrides are applied via ITEM_TO_TAB in 1.21.1
        // (no ItemAccess needed — see ensureInitialized)
        if (!namespaceCacheBuilt) buildNamespaceCache();
        // This is a no-op on 1.21.1 — items are mapped via ITEM_TO_TAB
    }

    // ── Stubs ──────────────────────────────────────────────────────

    public static void registerNewGroup(ItemStack stack) {}
    public static boolean isOwOLoaded() { return isOwOLoaded; }

    public static void rbip$renderOwo(GuiGraphics g, RecipeBookTabButton b) {}
    public static void rbip$renderOwo(GuiGraphics g, RecipeBookComponent c, RecipeBookTabButton b) {}
}

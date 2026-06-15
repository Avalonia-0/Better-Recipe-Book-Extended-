package com.alonie.recipebookispain_extended;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

    /** The currently-selected creative tab (set by RecipeBookWidgetMixin). */
    public static CreativeModeTab activeCreativeTab;

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
        sb.append("  config           = ").append(BetterRecipeBook.config).append("\n");
        if (BetterRecipeBook.config != null && BetterRecipeBook.config.rbip != null) {
            sb.append("  config.rbip.enableRecipeBookIsPain = ")
              .append(BetterRecipeBook.config.rbip.enableRecipeBookIsPain).append("\n");
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
        initAttempted = true;

        try {
            CRAFTING_LIST.clear();
            CRAFTING_SEARCH_LIST.clear();
            TAB_ITEMS.clear();
            ITEM_TO_TAB.clear();

            for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
                if (!shouldMirror(tab)) continue;
                try {
                    Set<Item> items = new HashSet<>();
                    for (ItemStack stack : tab.getDisplayItems()) {
                        if (!stack.isEmpty()) {
                            Item item = stack.getItem();
                            items.add(item);
                            // Store item→tab mapping (first tab wins)
                            ITEM_TO_TAB.putIfAbsent(item, tab);
                        }
                    }
                    if (!items.isEmpty()) {
                        TAB_ITEMS.put(tab, items);
                        CRAFTING_LIST.add(tab);
                        CRAFTING_SEARCH_LIST.add(tab);
                    }
                } catch (Exception e) {
                    LOGGER.error("[RBIP] Error processing tab: {}", tab.getDisplayName().getString(), e);
                }
            }
            initialized = true;
            LOGGER.info("[RBIP] OK — {} tabs, {} items mapped", CRAFTING_LIST.size(), ITEM_TO_TAB.size());
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

    public static void onConfigChanged() {
        LOGGER.info("[RBIP] onConfigChanged()");
        initialized = false;
        initAttempted = false;
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

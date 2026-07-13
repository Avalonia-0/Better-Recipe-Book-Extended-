package com.alonie.recipebookispain_extended;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.alonie.recipebookispain_extended.access.ItemAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.gui.screens.recipebook.SearchRecipeBookCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.ExtendedRecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
     * Key: 物品注册ID, Value: 目标创造标签页的注册路径 (如 "redstone_blocks")
     */
    private static final Map<Identifier, String> TAB_OVERRIDES = new HashMap<>();

    static {
        // 红石火把 → 重定向到红石方块标签页（原版归在功能方块标签）
        TAB_OVERRIDES.put(Identifier.fromNamespaceAndPath("minecraft", "redstone_torch"), "redstone_blocks");
        TAB_OVERRIDES.put(Identifier.fromNamespaceAndPath("minecraft", "redstone_wall_torch"), "redstone_blocks");
    }

    // ------------------------------------------------
    //  Initialisation
    // ------------------------------------------------

    public static synchronized void ensureInitialized() {
        if (initialized) return;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) {
            LOGGER.debug("[RBIP] Delaying recipe book init until a client world is available");
            return;
        }

        try {
            CreativeModeTabs.tryRebuildTabContents(FeatureFlags.DEFAULT_FLAGS, false, client.level.registryAccess());
        } catch (Exception e) {
            LOGGER.warn("[RBIP] Could not refresh creative item groups before building recipe book tabs", e);
        }

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

        LOGGER.info("[RBIP] recipe book init complete; mirrored {} creative groups", MIRRORED_ITEM_GROUPS.size());
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
            LOGGER.info("[RBIP] Namespace override: {} items rerouted to their own creative tabs", overridden);
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

    private static boolean shouldMirror(CreativeModeTab tab) {
        return tab.getType() != CreativeModeTab.Type.INVENTORY
                && tab.getType() != CreativeModeTab.Type.HOTBAR
                && tab.getType() != CreativeModeTab.Type.SEARCH;
    }

    public static void registerNewGroup(CreativeModeTab group) {
        if (!shouldMirror(group)) return;
        if (RECIPE_BOOK_GROUP_TO_ITEM_GROUP.inverse().containsKey(group)) return;
        ExtendedRecipeBookCategory rg = new RecipeBookCategory();
        RECIPE_BOOK_GROUP_TO_ITEM_GROUP.put(rg, group);
        MIRRORED_ITEM_GROUPS.add(group);
        CRAFTING_LIST.add(rg);
        CRAFTING_SEARCH_LIST.add(rg);
        LOGGER.info("[RBIP] Late-registered group: {}", group.getDisplayName().getString());
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
        return BLAST_FURNACE_BOOK_GROUP_TO_ITEM_GROUP.get(recipeBookGroup);
    }

    public static ExtendedRecipeBookCategory toRecipeBookGroup(CreativeModeTab tab) {
        return RECIPE_BOOK_GROUP_TO_ITEM_GROUP.inverse().get(tab);
    }

    public static ExtendedRecipeBookCategory toRecipeBookGroup(ItemStack stack) {
        ensureInitialized();
        if (stack.isEmpty()) return null;

        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null) {
            // ── 标签页覆盖检查 ─────────────────────────────────
            String overrideTabPath = TAB_OVERRIDES.get(id);
            if (overrideTabPath != null) {
                CreativeModeTab overrideTab = namespaceCache.get(overrideTabPath);
                if (overrideTab != null) {
                    ExtendedRecipeBookCategory group = toRecipeBookGroup(overrideTab);
                    if (group != null) return group;
                }
            }

            CreativeModeTab nsGroup = namespaceCache.get(id.getNamespace());
            if (nsGroup != null) {
                return toRecipeBookGroup(nsGroup);
            }
        }

        return ((ItemAccess) stack.getItem()).rbip$getPossibleGroup()
                .map(RecipeBookIsPain::toRecipeBookGroup)
                .orElse(null);
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

        for (CreativeModeTab tab : MIRRORED_ITEM_GROUPS) {
            Optional.ofNullable(toRecipeBookGroup(tab))
                    .map(group -> new RecipeBookComponent.TabInfo(tab.getIconItem(), Optional.empty(), group))
                    .ifPresent(expandedTabs::add);
        }
        return expandedTabs;
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
        for (CreativeModeTab tab : MIRRORED_ITEM_GROUPS) {
            if (!activeTabs.contains(tab)) continue;
            ExtendedRecipeBookCategory group = groupMap.inverse().get(tab);
            if (group != null) {
                expandedTabs.add(new RecipeBookComponent.TabInfo(tab.getIconItem(), Optional.empty(), group));
            }
        }
        return expandedTabs;
    }

    public static ExtendedRecipeBookCategory toFurnaceRecipeBookGroup(ItemStack stack, FurnaceVariant type) {
        return switch (type) {
            case SMOKER -> toFurnaceRecipeBookGroup(stack, SMOKER_BOOK_GROUP_TO_ITEM_GROUP);
            case BLAST_FURNACE -> toFurnaceRecipeBookGroup(stack, BLAST_FURNACE_BOOK_GROUP_TO_ITEM_GROUP);
            default -> toFurnaceRecipeBookGroup(stack, FURNACE_BOOK_GROUP_TO_ITEM_GROUP);
        };
    }

    private static ExtendedRecipeBookCategory toFurnaceRecipeBookGroup(ItemStack stack,
            BiMap<ExtendedRecipeBookCategory, CreativeModeTab> groupMap) {
        ensureInitialized();
        if (stack.isEmpty()) return null;

        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null) {
            // ── 标签页覆盖检查（与 toRecipeBookGroup 一致） ──────
            String overrideTabPath = TAB_OVERRIDES.get(id);
            if (overrideTabPath != null) {
                CreativeModeTab overrideTab = namespaceCache.get(overrideTabPath);
                if (overrideTab != null) {
                    ExtendedRecipeBookCategory group = groupMap.inverse().get(overrideTab);
                    if (group != null) return group;
                }
            }

            CreativeModeTab nsGroup = namespaceCache.get(id.getNamespace());
            if (nsGroup != null) {
                return groupMap.inverse().get(nsGroup);
            }
        }

        return ((ItemAccess) stack.getItem()).rbip$getPossibleGroup()
                .map(tab -> groupMap.inverse().get(tab))
                .orElse(null);
    }

    // ------------------------------------------------
    //  Icon rendering — owo-lib animated icons
    // ------------------------------------------------

    public static boolean rbip$renderOwo(GuiGraphicsExtractor context, int i, RecipeBookTabButton widget, CreativeModeTab group) {
        return rbip$renderOwo(context, widget.getX() + 9 + i, widget.getY() + 5, group);
    }

    public static boolean rbip$renderOwo(GuiGraphicsExtractor context, int x, int y, CreativeModeTab group) {
        // owo-lib animated icons — optional compat, not available at compile time
        return false;
    }

}

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
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RecipeBookIsPain {

    public static final Logger LOGGER = LogManager.getLogger("RBIP");
    public static PlatformAbstractions PLATFORM;
    public static boolean isOwOLoaded = false;

    public static final List<ExtendedRecipeBookCategory> CRAFTING_SEARCH_LIST = new ArrayList<>();
    public static final List<ExtendedRecipeBookCategory> CRAFTING_LIST = new ArrayList<>();

    public static final BiMap<ExtendedRecipeBookCategory, CreativeModeTab> RECIPE_BOOK_GROUP_TO_ITEM_GROUP = HashBiMap.create();
    private static final List<CreativeModeTab> MIRRORED_ITEM_GROUPS = new ArrayList<>();
    private static boolean initialized;

    private static final Map<String, CreativeModeTab> namespaceCache = new HashMap<>();
    private static boolean namespaceCacheBuilt;

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
        return RECIPE_BOOK_GROUP_TO_ITEM_GROUP.get(recipeBookGroup);
    }

    public static ExtendedRecipeBookCategory toRecipeBookGroup(CreativeModeTab tab) {
        return RECIPE_BOOK_GROUP_TO_ITEM_GROUP.inverse().get(tab);
    }

    public static ExtendedRecipeBookCategory toRecipeBookGroup(ItemStack stack) {
        ensureInitialized();
        if (stack.isEmpty()) return null;

        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null) {
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
    //  Icon rendering — owo-lib animated icons
    // ------------------------------------------------

    public static boolean rbip$renderOwo(GuiGraphicsExtractor context, int i, RecipeBookTabButton widget, CreativeModeTab group) {
        return rbip$renderOwo(context, widget.getX() + 9 + i, widget.getY() + 5, group);
    }

    public static boolean rbip$renderOwo(GuiGraphicsExtractor context, int x, int y, CreativeModeTab group) {
        // owo-lib not bundled — use standard icon rendering
        return false;
    }

}

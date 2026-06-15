package com.alonie.recipebookispain_extended;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecipeBookIsPain {

    public static final Logger LOGGER = LogManager.getLogger("RBIP");
    public static PlatformAbstractions PLATFORM;
    public static boolean isOwOLoaded = false;

    public static final List<Object> CRAFTING_SEARCH_LIST = new ArrayList<>();
    public static final List<Object> CRAFTING_LIST = new ArrayList<>();
    private static boolean initialized;

    private static final Map<String, CreativeModeTab> namespaceCache = new HashMap<>();
    private static boolean namespaceCacheBuilt;

    // -- Config-aware init -------------------------------------------------

    public static synchronized void ensureInitialized() {
        if (!RecipeBookIsPainExtendedConfig.enabled()) {
            if (initialized) {
                LOGGER.info("[RBIP] Disabled via config — clearing state");
                initialized = false;
                CRAFTING_LIST.clear();
                CRAFTING_SEARCH_LIST.clear();
            }
            return;
        }
        if (initialized) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        try {
            // Scan creative mode tabs and map them for recipe book groups
            CreativeModeTabs.allTabs().forEach(tab -> {
                CRAFTING_LIST.add(tab);
                CRAFTING_SEARCH_LIST.add(tab);
            });
            initialized = true;
            LOGGER.info("[RBIP] Initialized with {} creative tabs", CRAFTING_LIST.size());
        } catch (Exception e) {
            LOGGER.error("[RBIP] Failed to initialize creative tab mappings", e);
        }
    }

    /** Called by external code on config change to re-initialize. */
    public static void onConfigChanged() {
        initialized = false;
        CRAFTING_LIST.clear();
        CRAFTING_SEARCH_LIST.clear();
        namespaceCacheBuilt = false;
        namespaceCache.clear();
        if (RecipeBookIsPainExtendedConfig.enabled()) {
            ensureInitialized();
        }
    }

    // -- Namespace overrides -----------------------------------------------

    public static void buildNamespaceCache() {
        if (namespaceCacheBuilt) return;
        namespaceCacheBuilt = true;
    }

    public static CreativeModeTab applyNamespaceOverrides(ItemStack stack) { return null; }
    public static void registerNewGroup(ItemStack stack) {}
    public static boolean isOwOLoaded() { return false; }

    // -- OWO compat stubs --------------------------------------------------

    public static void rbip$renderOwo(GuiGraphics guiGraphics, RecipeBookTabButton button) {}
    public static void rbip$renderOwo(GuiGraphics guiGraphics, RecipeBookComponent component, RecipeBookTabButton button) {}
}

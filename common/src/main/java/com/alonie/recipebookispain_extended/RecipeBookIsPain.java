package com.alonie.recipebookispain_extended;

import com.alonie.brbe.BetterRecipeBook;
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
    public static boolean isOwOLoaded;

    public static final List<CreativeModeTab> CRAFTING_SEARCH_LIST = new ArrayList<>();
    public static final List<CreativeModeTab> CRAFTING_LIST = new ArrayList<>();
    private static boolean initialized;
    private static boolean initAttempted;

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
        sb.append("  CreativeModeTabs = ").append(CreativeModeTabs.allTabs().size()).append(" tabs\n");
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
            CreativeModeTabs.allTabs().forEach(tab -> {
                CRAFTING_LIST.add(tab);
                CRAFTING_SEARCH_LIST.add(tab);
            });
            initialized = true;
            LOGGER.info("[RBIP] OK — {} tabs", CRAFTING_LIST.size());
        } catch (Exception e) {
            LOGGER.error("[RBIP] Init failed", e);
        }
    }

    public static void onConfigChanged() {
        LOGGER.info("[RBIP] onConfigChanged()");
        initialized = false;
        initAttempted = false;
        CRAFTING_LIST.clear();
        CRAFTING_SEARCH_LIST.clear();
        namespaceCacheBuilt = false;
        namespaceCache.clear();
        if (RecipeBookIsPainExtendedConfig.enabled()) ensureInitialized();
    }

    // ── Stubs ──────────────────────────────────────────────────────

    public static void buildNamespaceCache() { namespaceCacheBuilt = true; }
    public static CreativeModeTab applyNamespaceOverrides(ItemStack stack) { return null; }
    public static void registerNewGroup(ItemStack stack) {}
    public static boolean isOwOLoaded() { return isOwOLoaded; }

    public static void rbip$renderOwo(GuiGraphics g, RecipeBookTabButton b) {}
    public static void rbip$renderOwo(GuiGraphics g, RecipeBookComponent c, RecipeBookTabButton b) {}
}

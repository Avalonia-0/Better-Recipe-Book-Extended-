package com.alonie.recipebookispain_extended;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.world.item.CreativeModeTab;
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

    // 1.21.1 stub — using Object as placeholder for RecipeBookCategory (enum in 1.21.1)
    public static final List<Object> CRAFTING_SEARCH_LIST = new ArrayList<>();
    public static final List<Object> CRAFTING_LIST = new ArrayList<>();
    private static boolean initialized;

    private static final Map<String, CreativeModeTab> namespaceCache = new HashMap<>();
    private static boolean namespaceCacheBuilt;

    public static synchronized void ensureInitialized() {
        if (initialized) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        initialized = true;
        LOGGER.info("[RBIP] Initialized (1.21.1 stub)");
    }

    public static void buildNamespaceCache() {
        if (namespaceCacheBuilt) return;
        namespaceCacheBuilt = true;
    }

    public static CreativeModeTab applyNamespaceOverrides(ItemStack stack) { return null; }
    public static void registerNewGroup(ItemStack stack) {}
    public static boolean isOwOLoaded() { return false; }
    public static void rbip$renderOwo(GuiGraphics guiGraphics, RecipeBookTabButton button) {}
    public static void rbip$renderOwo(GuiGraphics guiGraphics, RecipeBookComponent component, RecipeBookTabButton button) {}
}

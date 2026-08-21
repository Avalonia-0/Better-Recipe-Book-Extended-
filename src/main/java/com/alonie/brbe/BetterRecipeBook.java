package com.alonie.brbe;

import com.mojang.blaze3d.platform.InputConstants;
import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.config.AppContext;
import com.alonie.brbe.config.BrbeConfig;
import com.alonie.brbe.config.ConfigEventBus;
import com.alonie.brbe.loaders.PotionLoader;
import com.alonie.brbe.pin.JsonPinStore;
import com.alonie.brbe.pin.TabPinManager;
import com.alonie.brbe.util.PartialCraftingUtil;
import com.alonie.brbe.util.BRBHelper;
import com.alonie.brbe.cache.VanillaRecipeCache;
import com.alonie.brbe.util.RecipeUnlockUtil;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BetterRecipeBook {

    public static final String MOD_ID = "zzzbrbe";

    public static int queuedScroll;
    public static boolean isFilteringNone;

    public static BrbeConfig config;
    public static ConfigHolder<BrbeConfig> configHolder;

    public static PinnedRecipeManager pinnedRecipeManager;
    public static InstantCraftingManager instantCraftingManager;
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "category")
    );

    public static final KeyMapping PIN_MAPPING = new KeyMapping(
            "key.zzzbrbe.pin",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_F,
            KEY_CATEGORY
    );

    public static final KeyMapping RECIPE_VIEW_MAPPING = new KeyMapping(
            "key.zzzbrbe.recipeView",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_R,
            KEY_CATEGORY
    );

    public static final KeyMapping USAGE_VIEW_MAPPING = new KeyMapping(
            "key.zzzbrbe.usageView",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_U,
            KEY_CATEGORY
    );



    public static BRBHelper.Book BREWING;
    public static BRBHelper.Book SMITHING;

    public static BRBBookCategories.Category BREWING_POTION;
    public static BRBBookCategories.Category BREWING_SPLASH_POTION;
    public static BRBBookCategories.Category BREWING_LINGERING_POTION;
    public static BRBBookCategories.Category SMITHING_SEARCH;
    public static BRBBookCategories.Category SMITHING_TRANSFORM;
    public static BRBBookCategories.Category SMITHING_TRIM;

    /** The new dependency-injection root.  Created once in {@link #init()}. */
    private static AppContext appContext;

    /** Access the new AppContext (available after init()). */
    public static AppContext ctx() {
        return appContext;
    }

    /** Delegates to {@link AppContext#ensureCategories()}. Bridges static fields. */
    public static void ensureCategories() {
        if (appContext == null) return;
        appContext.ensureCategories();
        // Bridge to legacy static fields
        BREWING = appContext.brewingBook();
        SMITHING = appContext.smithingBook();
        BREWING_POTION = appContext.brewingPotion();
        BREWING_SPLASH_POTION = appContext.brewingSplashPotion();
        BREWING_LINGERING_POTION = appContext.brewingLingeringPotion();
        SMITHING_SEARCH = appContext.smithingSearch();
        SMITHING_TRANSFORM = appContext.smithingTransform();
        SMITHING_TRIM = appContext.smithingTrim();
    }

    public static void init() {
        PotionLoader.init();

        queuedScroll = 0;
        isFilteringNone = true;

        // Register config (existing logic, unchanged)
        try {
            AutoConfig.register(BrbeConfig.class, Toml4jConfigSerializer::new);

            configHolder = AutoConfig.getConfigHolder(BrbeConfig.class);
            config = configHolder.getConfig();
        } catch (Exception e) {
            BetterRecipeBook.LOGGER.warn("[BRBE] Config error: {}", e.getMessage());
        }

        // -- New architecture: create the DI root -------------------------------
        if (config != null && configHolder != null) {
            appContext = AppContext.create(config, configHolder);

            // Populate backward-compatible static fields from AppContext
            pinnedRecipeManager = appContext.pins();
            instantCraftingManager = appContext.instantCraft();

            // When partial marking config changes, invalidate caches immediately
            appContext.events().subscribe(ConfigEventBus.PartialCraftingChanged.class, event -> {
                PartialCraftingUtil.invalidateCaches();
            });

            // When any config field changes, update static reference + request UI refresh.
            appContext.events().subscribe(ConfigEventBus.ConfigChanged.class, event -> {
                boolean unlockChanged = config.newRecipes.unlockAll != event.config().newRecipes.unlockAll;
                config = event.config();
                appContext.events().requestConfigRefresh();
                if (unlockChanged) {
                    RecipeUnlockUtil.syncToConfig();
                }
            });

            // Wire async Pin I/O
            // Guard: Minecraft.getInstance() is null during NeoForge bootstrap.
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                JsonPinStore pinStore = new JsonPinStore(mc.gameDirectory.toPath());
                pinnedRecipeManager.setStore(pinStore);
                // RBIP 标签固定存储与查询对象 pin / 配方书 pin 同一目录（gameDir）。
                TabPinManager.init(mc.gameDirectory.toPath());
            }

            // Load pins
            pinnedRecipeManager.read();
        } else {
            // Fallback: config failed, create services directly
            pinnedRecipeManager = new PinnedRecipeManager();
            pinnedRecipeManager.read();
            instantCraftingManager = new InstantCraftingManager();
        }

        VanillaRecipeCache.init();
    }
}

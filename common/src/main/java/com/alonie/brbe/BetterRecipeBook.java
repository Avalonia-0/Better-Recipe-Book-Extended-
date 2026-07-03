package com.alonie.brbe;

import com.mojang.blaze3d.platform.InputConstants;
import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.config.AppContext;
import com.alonie.brbe.config.Config;
import com.alonie.brbe.config.ConfigEventBus;
import com.alonie.brbe.loaders.PotionLoader;
import com.alonie.brbe.pin.JsonPinStore;
import com.alonie.brbe.util.BRBHelper;
import com.alonie.brbe.util.BrbeDiagnostic;
import com.alonie.brbe.util.PartialCraftingUtil;
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

    public static final String MOD_ID = "brbe";

    public static int queuedScroll;
    public static boolean isFilteringNone;

    public static Config config;
    public static ConfigHolder<Config> configHolder;

    public static PinnedRecipeManager pinnedRecipeManager;
    public static InstantCraftingManager instantCraftingManager;
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    /** The new dependency-injection root.  Created once in {@link #init()}. */
    private static AppContext appContext;

    /** Access the new AppContext (available after init()). */
    public static AppContext ctx() {
        return appContext;
    }

    private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "category")
    );

    public static final KeyMapping PIN_MAPPING = new KeyMapping(
            "key.brbe.pin",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_F,
            KEY_CATEGORY
    );

    public static final KeyMapping RECIPE_VIEW_MAPPING = new KeyMapping(
            "key.brbe.recipeView",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_R,
            KEY_CATEGORY
    );

    public static final KeyMapping USAGE_VIEW_MAPPING = new KeyMapping(
            "key.brbe.usageView",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_U,
            KEY_CATEGORY
    );

    /** Diagnostic dump key (F8).  Writes brbe-diagnostic.log to game dir. */
    public static final KeyMapping DIAGNOSTIC_MAPPING = new KeyMapping(
            "key.brbe.diagnostic",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_F8,
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

    private static boolean categoriesInitialized = false;

    public static synchronized void ensureCategories() {
        if (categoriesInitialized) return;
        AppContext ctx = appContext;
        if (ctx == null) return;                 // not ready — retry next call
        try {
            BREWING = ctx.brewingBook();
            SMITHING = ctx.smithingBook();
            BREWING_POTION = ctx.brewingPotion();
            BREWING_SPLASH_POTION = ctx.brewingSplashPotion();
            BREWING_LINGERING_POTION = ctx.brewingLingeringPotion();
            SMITHING_SEARCH = ctx.smithingSearch();
            SMITHING_TRANSFORM = ctx.smithingTransform();
            SMITHING_TRIM = ctx.smithingTrim();
            categoriesInitialized = true;        // success — never retry
        } catch (Exception e) {
            // init failed (e.g. registry not bound) — leave flag false so
            // the next call retries instead of permanently skipping.
        }
    }

    public static void init() {
        PotionLoader.init();

        queuedScroll = 0;
        isFilteringNone = true;

        // -- New architecture: create the DI root first -----------------------
        // AppContext handles AutoConfig registration, ConfigEventBus creation,
        // and service instantiation internally.
        try {
            appContext = AppContext.create();

            // Populate backward-compatible static fields from AppContext
            config = appContext.config();
            configHolder = appContext.configHolder();
            pinnedRecipeManager = appContext.pins();
            instantCraftingManager = appContext.instantCraft();

            // When partial crafting settings change, invalidate caches
            appContext.events().subscribe(ConfigEventBus.PartialCraftingChanged.class, event -> {
                PartialCraftingUtil.invalidateCaches();
            });

            // Wire config changes through the event bus
            appContext.events().subscribe(ConfigEventBus.ConfigChanged.class, event -> {
                config = event.config();
                appContext.events().requestConfigRefresh();
                RecipeUnlockUtil.syncToConfig();
                BrbeDiagnostic.dump();
            });

            // -- Wire async Pin I/O -----------------------------------------------
            // Guard: Minecraft.getInstance() is null during NeoForge bootstrap.
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                JsonPinStore pinStore = new JsonPinStore(mc.gameDirectory.toPath());
                pinnedRecipeManager.setStore(pinStore);
            }
        } catch (Exception e) {
            BetterRecipeBook.LOGGER.warn("[BRBE] Config error: {}", e.getMessage());
            // Fallback: create services directly
            pinnedRecipeManager = new PinnedRecipeManager();
            instantCraftingManager = new InstantCraftingManager();
        }

        pinnedRecipeManager.read();
        VanillaRecipeCache.init();

        // -- Startup diagnostic: verify all new architecture systems ----------
        BrbeDiagnostic.dump();

        // KeyMapping registration moved to platform entry points
        // KeyBindingHelper.registerKeyBinding(PIN_MAPPING);
        // KeyBindingHelper.registerKeyBinding(RECIPE_VIEW_MAPPING);
        // KeyBindingHelper.registerKeyBinding(USAGE_VIEW_MAPPING);
    }
}

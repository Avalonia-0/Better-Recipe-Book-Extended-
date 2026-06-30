package com.alonie.brbe;

import com.mojang.blaze3d.platform.InputConstants;
import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.config.AppContext;
import com.alonie.brbe.config.Config;
import com.alonie.brbe.config.ConfigEventBus;
import com.alonie.brbe.loaders.PotionLoader;
import com.alonie.brbe.pin.JsonPinStore;
import com.alonie.brbe.util.BRBHelper;
import com.alonie.brbe.cache.VanillaRecipeCache;
import com.alonie.brbe.util.RecipeUnlockUtil;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

    public static BRBHelper.Book BREWING;
    public static BRBHelper.Book SMITHING;

    public static BRBBookCategories.Category BREWING_POTION;
    public static BRBBookCategories.Category BREWING_SPLASH_POTION;
    public static BRBBookCategories.Category BREWING_LINGERING_POTION;
    public static BRBBookCategories.Category SMITHING_SEARCH;
    public static BRBBookCategories.Category SMITHING_TRANSFORM;
    public static BRBBookCategories.Category SMITHING_TRIM;

    private static boolean categoriesInitialized = false;

    /** The new dependency-injection root.  Created once in {@link #init()}. */
    private static AppContext appContext;

    /** Access the new AppContext (available after init()). */
    public static AppContext ctx() {
        return appContext;
    }

    public static synchronized void ensureCategories() {
        if (categoriesInitialized) return;
        categoriesInitialized = true;
        BREWING = BRBHelper.createBook(MOD_ID, "brewing_stand");
        SMITHING = BRBHelper.createBook(MOD_ID, "smithing_table");
        BREWING_POTION = BREWING.createCategory(new ItemStack(Items.POTION));
        BREWING_SPLASH_POTION = BREWING.createCategory(new ItemStack(Items.SPLASH_POTION));
        BREWING_LINGERING_POTION = BREWING.createCategory(new ItemStack(Items.LINGERING_POTION));
        SMITHING_SEARCH = SMITHING.createSearch();
        SMITHING_TRANSFORM = SMITHING.createCategory(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
        SMITHING_TRIM = SMITHING.createCategory(new ItemStack(Items.NETHERITE_CHESTPLATE));
    }

    public static void init() {
        PotionLoader.init();

        queuedScroll = 0;
        isFilteringNone = true;

        // Register config (existing logic, unchanged)
        try {
            AutoConfig.register(Config.class, Toml4jConfigSerializer::new);

            configHolder = AutoConfig.getConfigHolder(Config.class);
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

            // Wire legacy config save listener for unlock recipes
            configHolder.registerSaveListener((holder, cfg) -> {
                boolean unlockChanged = config.newRecipes.unlockAll != cfg.newRecipes.unlockAll;
                BetterRecipeBook.config = cfg;
                if (unlockChanged) {
                    RecipeUnlockUtil.syncToConfig();
                }
                return InteractionResult.SUCCESS;
            });

            // Wire async Pin I/O
            JsonPinStore pinStore = new JsonPinStore(Minecraft.getInstance().gameDirectory.toPath());
            pinnedRecipeManager.setStore(pinStore);

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

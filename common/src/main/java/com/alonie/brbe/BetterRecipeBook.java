package com.alonie.brbe;

import com.mojang.blaze3d.platform.InputConstants;
import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.config.Config;
import com.alonie.brbe.compat.rei.ReiCompat;
import com.alonie.brbe.loaders.PotionLoader;
import com.alonie.brbe.util.BRBHelper;
import com.alonie.brbe.util.BrbeLogger;
import com.alonie.brbe.util.PartialCraftingUtil;
import com.alonie.brbe.util.RecipeUnlockUtil;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BetterRecipeBook {

    public static final String MOD_ID = "brbe";

    private static int queuedScroll;

    /**
     * Set to {@code true} by the config save listener when any config value
     * changes.  Recipe book components check this flag on each render frame
     * and call {@code updateCollections()} to refresh the display.  Reset to
     * {@code false} after the refresh is triggered.
     */
    public static volatile boolean configChanged = false;

    public static Config config;
    public static ConfigHolder<Config> configHolder;

    public static PinnedRecipeManager pinnedRecipeManager;
    public static InstantCraftingManager instantCraftingManager;
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public static int getQueuedScroll() { return queuedScroll; }

    public static void setQueuedScroll(int value) { queuedScroll = value; }

    public static void addQueuedScroll(int delta) { queuedScroll += delta; }

    public static final KeyMapping PIN_MAPPING = new KeyMapping(
            "key.brbe.pin",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_F,
            "category.brbe"
    );

    public static final KeyMapping RECIPE_VIEW_MAPPING = new KeyMapping(
            "key.brbe.recipeView",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_R,
            "category.brbe"
    );

    public static final KeyMapping USAGE_VIEW_MAPPING = new KeyMapping(
            "key.brbe.usageView",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_U,
            "category.brbe"
    );

    public static BRBHelper.Book BREWING = BRBHelper.createBook(MOD_ID, "brewing_stand");
    public static BRBHelper.Book SMITHING = BRBHelper.createBook(MOD_ID, "smithing_table");

    public static BRBBookCategories.Category BREWING_POTION = BREWING.createCategory(new ItemStack(Items.POTION));
    public static BRBBookCategories.Category BREWING_SPLASH_POTION = BREWING.createCategory(new ItemStack(Items.SPLASH_POTION));
    public static BRBBookCategories.Category BREWING_LINGERING_POTION = BREWING.createCategory(new ItemStack(Items.LINGERING_POTION));
    public static BRBBookCategories.Category SMITHING_SEARCH = SMITHING.createSearch();
    public static BRBBookCategories.Category SMITHING_TRANSFORM = SMITHING.createCategory(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
    public static BRBBookCategories.Category SMITHING_TRIM = SMITHING.createCategory(new ItemStack(Items.NETHERITE_CHESTPLATE));

    public static void init() {
        // Force early class-loading of CollectionCategory to avoid
        // NoClassDefFoundError on NeoForge when partialCraftingEnabled
        // is toggled ON for the first time (lazy loading issue).
        @SuppressWarnings("unused")
        Class<com.alonie.brbe.util.CollectionCategory> _cc = com.alonie.brbe.util.CollectionCategory.class;

        // Initialise debug logger (no-op unless -Dbrbe.debug=true)
        BrbeLogger.init(Minecraft.getInstance().gameDirectory.toPath());

        PotionLoader.init();
        ReiCompat.register();

        queuedScroll = 0;

        AutoConfig.register(Config.class, Toml4jConfigSerializer::new);

        configHolder = AutoConfig.getConfigHolder(Config.class);
        configHolder.registerSaveListener((holder, config) -> {
            // Log config transition
            Config old = BetterRecipeBook.config;
            BrbeLogger.log(BrbeLogger.Category.CONFIG,
                    "Save listener — configChanged SET, partialCraftingEnabled: %s→%s, partialMarkingEnabled: %s→%s, enablePinning: %s→%s, noGrouped: %s→%s",
                    old != null ? old.partialCraftingEnabled : "null",
                    config.partialCraftingEnabled,
                    old != null ? old.partialMarkingEnabled : "null",
                    config.partialMarkingEnabled,
                    old != null ? old.enablePinning : "null",
                    config.enablePinning,
                    old != null ? old.alternativeRecipes.noGrouped : "null",
                    config.alternativeRecipes.noGrouped);

            BetterRecipeBook.config = config;
            BetterRecipeBook.configChanged = true;
            RecipeUnlockUtil.syncToConfig();
            com.alonie.recipebookispain_extended.RecipeBookIsPain.onConfigChanged();
            return InteractionResult.SUCCESS;
        });
        config = configHolder.getConfig();

        pinnedRecipeManager = new PinnedRecipeManager();
        pinnedRecipeManager.read();
        instantCraftingManager = new InstantCraftingManager();

        // KeyMapping registration moved to platform entry points
        // KeyBindingHelper.registerKeyBinding(PIN_MAPPING);
        // KeyBindingHelper.registerKeyBinding(RECIPE_VIEW_MAPPING);
        // KeyBindingHelper.registerKeyBinding(USAGE_VIEW_MAPPING);
    }
}

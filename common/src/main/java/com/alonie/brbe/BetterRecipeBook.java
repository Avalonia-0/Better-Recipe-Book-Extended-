package com.alonie.brbe;

import com.mojang.blaze3d.platform.InputConstants;
import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.config.AppContext;
import com.alonie.brbe.config.Config;
import com.alonie.brbe.config.ConfigEventBus;
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

/**
 * Main mod class for Better Recipe Book Extended.
 *
 * <p>Backward-compatible static fields are maintained — they now delegate to
 * {@link AppContext} where applicable.  New code should inject dependencies
 * through {@code AppContext.instance()} rather than reaching for these
 * statics.</p>
 */
public class BetterRecipeBook {

    public static final String MOD_ID = "brbe";

    private static int queuedScroll;

    /**
     * @deprecated Use {@code AppContext.instance().events()} and subscribe to
     *             {@link ConfigEventBus.ConfigChanged} instead.  Kept for
     *             backward compatibility with existing mixins.
     */
    @Deprecated
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

    /** The new dependency-injection root.  Created once in {@link #init()}. */
    private static AppContext appContext;

    /** Access the new AppContext (available after init()). */
    public static AppContext ctx() {
        return appContext;
    }

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

        // -- New architecture: create the DI root first -----------------------
        appContext = AppContext.create();

        // -- Populate backward-compatible static fields from AppContext -------
        config = appContext.config();
        configHolder = appContext.configHolder();
        pinnedRecipeManager = appContext.pins();
        instantCraftingManager = appContext.instantCraft();

        // -- Wire the old configChanged flag to the new event bus -------------
        // When config changes through the event bus, also set the legacy flag
        // so existing mixins that poll configChanged continue to work.
        appContext.events().subscribe(ConfigEventBus.ConfigChanged.class, event -> {
            configChanged = true;
            RecipeUnlockUtil.syncToConfig();
            // RBIP callback — will be migrated to event bus subscription
            com.alonie.recipebookispain_extended.RecipeBookIsPain.onConfigChanged();
        });

        // -- Load pins --------------------------------------------------------
        pinnedRecipeManager.read();

        // KeyMapping registration moved to platform entry points
        // KeyBindingHelper.registerKeyBinding(PIN_MAPPING);
        // KeyBindingHelper.registerKeyBinding(RECIPE_VIEW_MAPPING);
        // KeyBindingHelper.registerKeyBinding(USAGE_VIEW_MAPPING);
    }
}

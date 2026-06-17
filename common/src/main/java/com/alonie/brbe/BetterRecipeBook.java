package com.alonie.brbe;

import com.mojang.blaze3d.platform.InputConstants;
import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.config.Config;
import com.alonie.brbe.loaders.PotionLoader;
import com.alonie.brbe.util.BRBHelper;
import com.alonie.brbe.util.RecipeUnlockUtil;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;
import net.minecraft.client.KeyMapping;
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

        // Cloth Config not yet available for 26.2 — skip registration gracefully.
        // Config no longer implements ConfigData to avoid runtime linkage, so
        // raw-type casts are needed to bypass AutoConfig's generic bound.
        try {
            AutoConfig.register(Config.class, Toml4jConfigSerializer::new);

            configHolder = AutoConfig.getConfigHolder(Config.class);
            configHolder.registerSaveListener((holder, cfg) -> {
                BetterRecipeBook.config = cfg;
                RecipeUnlockUtil.syncToConfig();
                return InteractionResult.SUCCESS;
            });
            config = configHolder.getConfig();
        } catch (Exception e) {
            BetterRecipeBook.LOGGER.warn("[BRBE] Config error: {}", e.getMessage());
        }

        pinnedRecipeManager = new PinnedRecipeManager();
        pinnedRecipeManager.read();
        instantCraftingManager = new InstantCraftingManager();

        // KeyMapping registration moved to platform entry points
        // KeyBindingHelper.registerKeyBinding(PIN_MAPPING);
        // KeyBindingHelper.registerKeyBinding(RECIPE_VIEW_MAPPING);
        // KeyBindingHelper.registerKeyBinding(USAGE_VIEW_MAPPING);
    }
}

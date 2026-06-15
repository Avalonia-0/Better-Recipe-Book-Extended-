package com.alonie.brbe;

import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.config.Config;
import com.alonie.brbe.compat.rei.ReiCompat;
import com.alonie.brbe.loaders.PotionLoader;
import com.alonie.brbe.util.BRBHelper;
import com.alonie.brbe.util.RecipeUnlockUtil;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BetterRecipeBook {

    public static final String MOD_ID = "brbe";

    private static int queuedScroll;

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
        PotionLoader.init();
        ReiCompat.register();

        queuedScroll = 0;

        AutoConfig.register(Config.class, Toml4jConfigSerializer::new);

        configHolder = AutoConfig.getConfigHolder(Config.class);
        configHolder.registerSaveListener((holder, config) -> {
            BetterRecipeBook.config = config;
            RecipeUnlockUtil.syncToConfig();
            com.alonie.recipebookispain_extended.RecipeBookIsPain.onConfigChanged();
            return InteractionResult.SUCCESS;
        });
        config = configHolder.getConfig();

        pinnedRecipeManager = new PinnedRecipeManager();
        pinnedRecipeManager.read();
        instantCraftingManager = new InstantCraftingManager();

        KeyMappingRegistry.register(PIN_MAPPING);
        KeyMappingRegistry.register(RECIPE_VIEW_MAPPING);
        KeyMappingRegistry.register(USAGE_VIEW_MAPPING);
    }
}

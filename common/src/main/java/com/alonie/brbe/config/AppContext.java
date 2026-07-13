package com.alonie.brbe.config;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.InstantCraftingManager;
import com.alonie.brbe.PinnedRecipeManager;
import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.layout.BookLayout;
import com.alonie.brbe.util.BRBHelper;
import me.shedaniel.autoconfig.ConfigHolder;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The single dependency-injection root for BRBE.
 *
 * <p>Config is registered externally by {@code BetterRecipeBook.init()} and
 * passed in — AppContext does NOT call {@code AutoConfig.register()} itself.
 * This avoids double-registration with the existing config setup in 26.1.2.</p>
 *
 * <p>All modules receive their dependencies through this context rather than
 * reaching into global state.</p>
 */
public final class AppContext {

    private static volatile AppContext INSTANCE;

    // -- Core services --------------------------------------------------------

    private volatile Config config;
    private final ConfigHolder<Config> configHolder;
    private final ConfigEventBus events;
    private final PinnedRecipeManager pinnedRecipeManager;
    private final InstantCraftingManager instantCraftingManager;
    private final BookLayout bookLayout;

    // -- Book / Category registries -------------------------------------------

    private final BRBHelper.Book brewing;
    private final BRBHelper.Book smithing;

    // -- Config change detection -----------------------------------------------
    // Cached values of recipe-relevant config fields.  Used to skip
    // unnecessary recipe-book refreshes when unrelated configs change
    // (e.g. instantCraft, hideReiJeiOverlay).

    private boolean cachedPartialCraftingEnabled;
    private boolean cachedPartialMarkingEnabled;
    private boolean cachedShowAllRecipesInSurvival;
    private boolean cachedNoGrouped;

    private void snapshotRecipeConfig(Config cfg) {
        this.cachedPartialCraftingEnabled = cfg.partialCraftingEnabled;
        this.cachedPartialMarkingEnabled = cfg.partialMarkingEnabled;
        this.cachedShowAllRecipesInSurvival = cfg.showAllRecipesInSurvival;
        this.cachedNoGrouped = cfg.alternativeRecipes.noGrouped;
    }

    private boolean recipeRelevantChanged(Config cfg) {
        return cfg.partialCraftingEnabled != this.cachedPartialCraftingEnabled
            || cfg.partialMarkingEnabled != this.cachedPartialMarkingEnabled
            || cfg.showAllRecipesInSurvival != this.cachedShowAllRecipesInSurvival
            || cfg.alternativeRecipes.noGrouped != this.cachedNoGrouped;
    }

    private volatile BRBBookCategories.Category brewingPotion;
    private volatile BRBBookCategories.Category brewingSplashPotion;
    private volatile BRBBookCategories.Category brewingLingeringPotion;
    private volatile BRBBookCategories.Category smithingSearch;
    private volatile BRBBookCategories.Category smithingTransform;
    private volatile BRBBookCategories.Category smithingTrim;
    private volatile boolean categoriesInitialized = false;

    private AppContext(Config config, ConfigHolder<Config> configHolder) {
        this.config = config;
        this.configHolder = configHolder;
        this.events = new ConfigEventBus();

        // Book registries — deferred category creation because ItemStack
        // construction may fail before the game registry is fully bound.
        this.brewing = BRBHelper.createBook("brbe", "brewing_stand");
        this.smithing = BRBHelper.createBook("brbe", "smithing_table");
        // categories populated by ensureCategories()

        // Services
        this.bookLayout = new BookLayout();
        this.pinnedRecipeManager = new PinnedRecipeManager();
        this.instantCraftingManager = new InstantCraftingManager();

        // Cache initial recipe-relevant config values for change detection
        snapshotRecipeConfig(this.config);

        // Wire config save listener through the event bus
        configHolder.registerSaveListener((holder, cfg) -> {
            this.config = cfg;
            events.publish(new ConfigEventBus.ConfigChanged(cfg));
            events.publish(new ConfigEventBus.PartialCraftingChanged(
                    cfg.partialCraftingEnabled, cfg.partialMarkingEnabled));
            events.publish(new ConfigEventBus.PinningChanged(true));
            events.publish(new ConfigEventBus.BookVisibilityChanged(cfg.enableBook));
            // Only refresh recipe book if recipe-relevant fields changed.
            // instantCraft, scrolling, hideReiJeiOverlay etc. do NOT
            // need a recipe-book refresh.
            if (recipeRelevantChanged(cfg)) {
                events.requestConfigRefresh();
                snapshotRecipeConfig(cfg);
            }
            return InteractionResult.SUCCESS;
        });

        INSTANCE = this;
    }

    // -- Singleton access -----------------------------------------------------

    /** Create the singleton. Called once from {@code BetterRecipeBook.init()}. */
    public static AppContext create(Config config, ConfigHolder<Config> configHolder) {
        if (INSTANCE != null) {
            throw new IllegalStateException("AppContext already created");
        }
        return new AppContext(config, configHolder);
    }

    /** Access the singleton. Throws if {@link #create} hasn't been called yet. */
    public static AppContext instance() {
        if (INSTANCE == null) {
            throw new IllegalStateException("AppContext not yet created — call create() first");
        }
        return INSTANCE;
    }

    // -- Lazy category init ---------------------------------------------------

    /** Lazy-init book categories.  ItemStack construction may fail before
     * the game registry is fully bound on some versions, so we defer until
     * first access. */
    public synchronized void ensureCategories() {
        if (categoriesInitialized) return;
        try {
            this.brewingPotion = brewing.createCategory(new ItemStack(Items.POTION));
            this.brewingSplashPotion = brewing.createCategory(new ItemStack(Items.SPLASH_POTION));
            this.brewingLingeringPotion = brewing.createCategory(new ItemStack(Items.LINGERING_POTION));
            this.smithingSearch = smithing.createSearch();
            this.smithingTransform = smithing.createCategory(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
            this.smithingTrim = smithing.createCategory(new ItemStack(Items.NETHERITE_CHESTPLATE));
            categoriesInitialized = true;
        } catch (Exception e) {
            // init failed — leave flag false so the next call retries
        }
    }

    // -- Getters --------------------------------------------------------------

    public Config config() { return config; }
    public ConfigHolder<Config> configHolder() { return configHolder; }
    public ConfigEventBus events() { return events; }
    public PinnedRecipeManager pins() { return pinnedRecipeManager; }
    public InstantCraftingManager instantCraft() { return instantCraftingManager; }
    public BookLayout bookLayout() { return bookLayout; }

    public BRBHelper.Book brewingBook() { ensureCategories(); return brewing; }
    public BRBHelper.Book smithingBook() { ensureCategories(); return smithing; }
    public BRBBookCategories.Category brewingPotion() { ensureCategories(); return brewingPotion; }
    public BRBBookCategories.Category brewingSplashPotion() { ensureCategories(); return brewingSplashPotion; }
    public BRBBookCategories.Category brewingLingeringPotion() { ensureCategories(); return brewingLingeringPotion; }
    public BRBBookCategories.Category smithingSearch() { ensureCategories(); return smithingSearch; }
    public BRBBookCategories.Category smithingTransform() { ensureCategories(); return smithingTransform; }
    public BRBBookCategories.Category smithingTrim() { ensureCategories(); return smithingTrim; }
}

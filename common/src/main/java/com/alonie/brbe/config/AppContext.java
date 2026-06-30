package com.alonie.brbe.config;

import com.alonie.brbe.InstantCraftingManager;
import com.alonie.brbe.PinnedRecipeManager;
import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.api.BRBBookSettings;
import com.alonie.brbe.compat.recipeviewer.RecipeViewerRegistry;
import com.alonie.brbe.layout.BookLayout;
import com.alonie.brbe.pin.JsonPinStore;
import com.alonie.brbe.pin.PinStore;
import com.alonie.brbe.util.BRBHelper;
import com.alonie.brbe.config.Config;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The single dependency-injection root for BRBE.
 *
 * <p>This replaces the old pattern of scattering {@code public static} mutable
 * fields across {@code BetterRecipeBook} and a dozen utility classes.
 * All modules receive their dependencies through this context rather than
 * reaching into global state.</p>
 *
 * <p>There is exactly <strong>one</strong> static accessor —
 * {@link #instance()} — which returns the singleton created during mod
 * initialisation.  Everything else is instance-scoped.</p>
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>{@link #create()} — called once from {@code Brbe.init()}</li>
 *   <li>{@link #instance()} — available thereafter for code that cannot
 *       receive DI (Mixin-injected classes, RBIP)</li>
 * </ol>
 */
public final class AppContext {

    private static volatile AppContext INSTANCE;

    // -- Core services --------------------------------------------------------

    private final Config config;
    private final ConfigHolder<Config> configHolder;
    private final ConfigEventBus events;
    private final PinnedRecipeManager pinnedRecipeManager;
    private final InstantCraftingManager instantCraftingManager;
    private final BookLayout bookLayout;
    private final RecipeViewerRegistry recipeViewers;

    // -- Book / Category registries -------------------------------------------

    private final BRBHelper.Book brewing;
    private final BRBHelper.Book smithing;
    private final BRBBookCategories.Category brewingPotion;
    private final BRBBookCategories.Category brewingSplashPotion;
    private final BRBBookCategories.Category brewingLingeringPotion;
    private final BRBBookCategories.Category smithingSearch;
    private final BRBBookCategories.Category smithingTransform;
    private final BRBBookCategories.Category smithingTrim;

    private AppContext() {
        // Register config first so we have a config snapshot to pass around.
        AutoConfig.register(Config.class, Toml4jConfigSerializer::new);
        this.configHolder = AutoConfig.getConfigHolder(Config.class);
        this.config = configHolder.getConfig();

        this.events = new ConfigEventBus();

        // Book and category registries (backward-compatible with existing static API)
        this.brewing = BRBHelper.createBook("brbe", "brewing_stand");
        this.smithing = BRBHelper.createBook("brbe", "smithing_table");
        this.brewingPotion = brewing.createCategory(new ItemStack(Items.POTION));
        this.brewingSplashPotion = brewing.createCategory(new ItemStack(Items.SPLASH_POTION));
        this.brewingLingeringPotion = brewing.createCategory(new ItemStack(Items.LINGERING_POTION));
        this.smithingSearch = smithing.createSearch();
        this.smithingTransform = smithing.createCategory(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
        this.smithingTrim = smithing.createCategory(new ItemStack(Items.NETHERITE_CHESTPLATE));

        // Services (Pin I/O deferred — needs gameDir from BetterRecipeBook.init())
        this.bookLayout = new BookLayout();
        this.recipeViewers = new RecipeViewerRegistry();
        this.pinnedRecipeManager = new PinnedRecipeManager();
        this.instantCraftingManager = new InstantCraftingManager();

        // Wire config save listener through the event bus
        configHolder.registerSaveListener((holder, cfg) -> {
            // Publish standardised events from the old Config object
            events.publish(new ConfigEventBus.ConfigChanged(cfg));
            events.publish(new ConfigEventBus.PartialCraftingChanged(
                    cfg.partialCraftingEnabled, cfg.partialMarkingEnabled));
            events.publish(new ConfigEventBus.PinningChanged(cfg.enablePinning));
            events.publish(new ConfigEventBus.BookVisibilityChanged(cfg.enableBook));
            return InteractionResult.SUCCESS;
        });

        INSTANCE = this;
    }

    // -- Singleton access -----------------------------------------------------

    /** Create the singleton.  Called once from {@code Brbe.init()}. */
    public static AppContext create() {
        if (INSTANCE != null) {
            throw new IllegalStateException("AppContext already created");
        }
        return new AppContext();
    }

    /** Access the singleton.  Throws if {@link #create()} hasn't been called yet. */
    public static AppContext instance() {
        if (INSTANCE == null) {
            throw new IllegalStateException("AppContext not yet created — call create() first");
        }
        return INSTANCE;
    }

    // -- Getters --------------------------------------------------------------

    public Config config() { return config; }
    public ConfigHolder<Config> configHolder() { return configHolder; }
    public ConfigEventBus events() { return events; }
    public PinnedRecipeManager pins() { return pinnedRecipeManager; }
    public InstantCraftingManager instantCraft() { return instantCraftingManager; }
    public BookLayout bookLayout() { return bookLayout; }
    public RecipeViewerRegistry recipeViewers() { return recipeViewers; }

    public BRBHelper.Book brewingBook() { return brewing; }
    public BRBHelper.Book smithingBook() { return smithing; }
    public BRBBookCategories.Category brewingPotion() { return brewingPotion; }
    public BRBBookCategories.Category brewingSplashPotion() { return brewingSplashPotion; }
    public BRBBookCategories.Category brewingLingeringPotion() { return brewingLingeringPotion; }
    public BRBBookCategories.Category smithingSearch() { return smithingSearch; }
    public BRBBookCategories.Category smithingTransform() { return smithingTransform; }
    public BRBBookCategories.Category smithingTrim() { return smithingTrim; }
}

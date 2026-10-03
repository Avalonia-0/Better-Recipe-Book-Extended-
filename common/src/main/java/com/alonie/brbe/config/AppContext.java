package com.alonie.brbe.config;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.InstantCraftingManager;
import com.alonie.brbe.PinnedRecipeManager;
import com.alonie.brbe.api.BRBBookCategories;
import com.alonie.brbe.api.BRBBookSettings;
import com.alonie.brbe.compat.recipeviewer.RecipeViewerRegistry;
import com.alonie.brbe.layout.BookLayout;
import com.alonie.brbe.pin.JsonPinStore;
import com.alonie.brbe.pin.PinStore;
import com.alonie.brbe.util.BRBHelper;
import com.alonie.brbe.config.BrbeConfig;
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

    private final BrbeConfig config;
    private final ConfigHolder<BrbeConfig> configHolder;
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

    /**
     * 配置加载前的 TOML 预处理（2026-10-03）：把**三个**旧布尔项改写成枚举值 ——
     * {@code naturalPageDirection} → {@code pageFlipDirection = "NATURAL"|"REGULAR"}、
     * {@code hideNoRecipeBookStationObjects} → {@code queryScope = "RECIPE_BOOK_ONLY"|"ALL_CATEGORIES"}、
     * {@code noGrouped} → {@code splitMode = "FULL"|"SELECTIVE"}。
     *
     * <p>字段类型换掉后旧值反序列化会抛异常 → Cloth 的 register 整体失败（玩家的其余设置一起丢），
     * 所以必须在 {@code AutoConfig.register} 之前把文件里那一行改掉。
     * 文件不存在 / 不含旧键时什么都不做。</p>
     */
    private static void migrateLegacyConfigValuesInToml() {
        try {
            net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft == null || minecraft.gameDirectory == null) return;
            java.nio.file.Path path = minecraft.gameDirectory.toPath()
                    .resolve("config").resolve("brbe.toml");
            if (!java.nio.file.Files.isRegularFile(path)) return;
            String text = java.nio.file.Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
            String updated = text
                    .replaceAll("(?m)^(\\s*)naturalPageDirection\\s*=\\s*true\\b",
                            "$1pageFlipDirection = \"NATURAL\"")
                    .replaceAll("(?m)^(\\s*)naturalPageDirection\\s*=\\s*false\\b",
                            "$1pageFlipDirection = \"REGULAR\"")
                    .replaceAll("(?m)^(\\s*)hideNoRecipeBookStationObjects\\s*=\\s*true\\b",
                            "$1queryScope = \"RECIPE_BOOK_ONLY\"")
                    .replaceAll("(?m)^(\\s*)hideNoRecipeBookStationObjects\\s*=\\s*false\\b",
                            "$1queryScope = \"ALL_CATEGORIES\"")
                    .replaceAll("(?m)^(\\s*)noGrouped\\s*=\\s*true\\b",
                            "$1splitMode = \"FULL\"")
                    .replaceAll("(?m)^(\\s*)noGrouped\\s*=\\s*false\\b",
                            "$1splitMode = \"SELECTIVE\"");
            if (updated.equals(text)) return;
            java.nio.file.Files.writeString(path, updated, java.nio.charset.StandardCharsets.UTF_8);
            BetterRecipeBook.LOGGER.info("[BRBE] Migrated brbe.toml legacy boolean options to enums");
        } catch (Exception e) {
            BetterRecipeBook.LOGGER.warn("[BRBE] legacy option TOML migration failed: {}", e.getMessage());
        }
    }

    private AppContext() {
        // 配置加载前的 TOML 预处理（2026-10-03）：两个旧布尔项 → 枚举值
        // （`naturalPageDirection` / `hideNoRecipeBookStationObjects` / `noGrouped`；枚举化必须的一次性搬运，
        //  必须早于 AutoConfig.register，否则旧布尔值会让整份配置反序列化失败）。
        migrateLegacyConfigValuesInToml();

        // Register config first so we have a config snapshot to pass around.
        AutoConfig.register(BrbeConfig.class, Toml4jConfigSerializer::new);
        this.configHolder = AutoConfig.getConfigHolder(BrbeConfig.class);
        this.config = configHolder.getConfig();

        // Populate backward-compatible static fields IMMEDIATELY — services
        // created below (InstantCraftingManager etc.) may access them.
        BetterRecipeBook.configHolder = this.configHolder;
        BetterRecipeBook.config = this.config;

        this.events = new ConfigEventBus();

        // Book and category registries (backward-compatible with existing static API)
        this.brewing = BRBHelper.createBook("brbe", "brewing_stand");
        this.smithing = BRBHelper.createBook("brbe", "smithing_table");
        this.brewingPotion = brewing.createCategory(new ItemStack(Items.POTION));
        this.brewingSplashPotion = brewing.createCategory(new ItemStack(Items.SPLASH_POTION));
        this.brewingLingeringPotion = brewing.createCategory(new ItemStack(Items.LINGERING_POTION));
        // 锻造台标签页 = 「升级模板 / 纹饰模板」两页（用户 2026-09-28 诉求：去掉"搜索"页）。
        // 本分支的标签列表取自 BetterRecipeBook 的静态类别（本 AppContext 里的 Book 是
        // 另一个对象、仅供诊断读取），两处保持一致：搜索类别不登记为标签页。
        this.smithingSearch = smithing.createUnlistedSearch();
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
            events.publish(new ConfigEventBus.PinningChanged(true));
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

    public BrbeConfig config() { return config; }
    public ConfigHolder<BrbeConfig> configHolder() { return configHolder; }
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

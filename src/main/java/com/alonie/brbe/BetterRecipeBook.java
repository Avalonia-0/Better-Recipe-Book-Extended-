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
import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.util.RecipeUnlockUtil;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.alonie.brbe.util.BrbeLogger;

public class BetterRecipeBook {

    public static final String MOD_ID = "brbe";

    public static int queuedScroll;
    public static boolean isFilteringNone;

    public static BrbeConfig config;
    public static ConfigHolder<BrbeConfig> configHolder;

    /** Last-seen unlockAll value, for change detection on config save (Cloth
     *  mutates the config object in place, so old/new cannot be compared by
     *  object identity). */
    private static boolean lastUnlockAllValue = true;

    public static PinnedRecipeManager pinnedRecipeManager;
    public static InstantCraftingManager instantCraftingManager;
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "category")
    );

    public static final KeyMapping PIN_MAPPING = new KeyMapping(
            "key.brbe.pin",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_A,
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

    /** 「锁定折叠物品」键：按住冻结折叠物品的自动轮换，配合滚轮逐格翻动。
     *  默认左 Alt（沿用原版「Alt 锁定」的手感）；运行时按配置字符串轮询物理键
     *  （见 {@code ClientCompat.isCycleLockDown}），不依赖本映射的事件状态——
     *  注册它只是为了在「按键绑定」界面里有一条可改的键位条目。 */
    public static final KeyMapping CYCLE_LOCK_MAPPING = new KeyMapping(
            "key.brbe.cycleLock",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_LALT,
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

    /**
     * 配置加载前的 TOML 预处理：把「标签模式」的旧枚举值 {@code "COMPACT"} 改写成
     * {@code "NAMESPACE"}（2026-10-01 三档并存后，那个常量的语义后代是命名空间档）。
     *
     * <p>为什么不在配置对象里迁移：枚举常量一旦从 enum 里删掉，旧 TOML 值反序列化就会抛异常
     * ——Cloth 的 register 会整体失败，玩家的其余设置也会一起丢。所以先把文件里的这一个值改掉，
     * 再让 AutoConfig 正常加载。文件不存在 / 不含旧值时什么都不做。</p>
     */
    private static void migrateLegacyTabModeInToml() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.gameDirectory == null) return;
            java.nio.file.Path path = minecraft.gameDirectory.toPath()
                    .resolve("config").resolve("brbe.toml");
            if (!java.nio.file.Files.isRegularFile(path)) return;
            String text = java.nio.file.Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
            String updated = text.replaceAll("(?m)^(\\s*tabMode\\s*=\\s*)[\"']COMPACT[\"']", "$1\"NAMESPACE\"");
            if (updated.equals(text)) return;
            java.nio.file.Files.writeString(path, updated, java.nio.charset.StandardCharsets.UTF_8);

            LOGGER.info("[BRBE] Migrated brbe.toml tabMode value: COMPACT -> NAMESPACE");
        } catch (Exception e) {
            LOGGER.warn("[BRBE] tabMode TOML migration failed: {}", e.getMessage());
        }
    }

    /**
     * 配置加载前的 TOML 预处理（2026-10-03）：把**四个**旧布尔项改写成枚举值 ——
     * {@code naturalPageDirection} → {@code pageFlipDirection = "NATURAL"|"REGULAR"}、
     * {@code hideNoRecipeBookStationObjects} → {@code queryScope = "RECIPE_BOOK_ONLY"|"ALL_CATEGORIES"}、
     * {@code previewMode} → {@code windowMode = "PERSISTENT"|"PREVIEW"}、
     * {@code noGrouped} → {@code splitMode = "FULL"|"SELECTIVE"}。
     *
     * <p>同 {@link #migrateLegacyTabModeInToml()}：字段类型换掉后，旧值反序列化会抛异常 →
     * Cloth 的 register 整体失败（玩家的其余设置一起丢）。所以先把文件里那一行改掉，
     * 再让 AutoConfig 正常加载。文件不存在 / 不含旧键时什么都不做。</p>
     */
    private static void migrateLegacyConfigValuesInToml() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
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
                    .replaceAll("(?m)^(\\s*)previewMode\\s*=\\s*true\\b",
                            "$1windowMode = \"PREVIEW\"")
                    .replaceAll("(?m)^(\\s*)previewMode\\s*=\\s*false\\b",
                            "$1windowMode = \"PERSISTENT\"")
                    .replaceAll("(?m)^(\\s*)noGrouped\\s*=\\s*true\\b",
                            "$1splitMode = \"FULL\"")
                    .replaceAll("(?m)^(\\s*)noGrouped\\s*=\\s*false\\b",
                            "$1splitMode = \"SELECTIVE\"");
            if (updated.equals(text)) return;
            java.nio.file.Files.writeString(path, updated, java.nio.charset.StandardCharsets.UTF_8);
            LOGGER.info("[BRBE] Migrated brbe.toml legacy boolean options to enums");
        } catch (Exception e) {
            LOGGER.warn("[BRBE] legacy option TOML migration failed: {}", e.getMessage());
        }
    }

    public static void init() {
        PotionLoader.init();

        queuedScroll = 0;
        isFilteringNone = true;

        // 配置加载前的 TOML 预处理（2026-10-01）：`tabMode = "COMPACT"` → `"NAMESPACE"`。
        // 必须在 AutoConfig.register 之前做——枚举常量改名后 GSON/Cloth 反序列化旧值会失败，
        // 那样整份配置都会走异常分支（等于配置丢失）。只改这一个值，其余原样保留。
        migrateLegacyTabModeInToml();

        // 配置加载前的 TOML 预处理（2026-10-03）：三个旧布尔项 → 枚举值
        // （`naturalPageDirection` / `hideNoRecipeBookStationObjects` / `previewMode` / `noGrouped`；
        //  "枚举化"必须的一次性搬运 —— 旧布尔值会让枚举反序列化失败、整份配置丢失）。
        migrateLegacyConfigValuesInToml();

        // Register config (existing logic, unchanged)
        try {
            AutoConfig.register(BrbeConfig.class, Toml4jConfigSerializer::new);

            configHolder = AutoConfig.getConfigHolder(BrbeConfig.class);
            config = configHolder.getConfig();

            // 配置迁移（2026-09-30）：旧布尔开关 compactTabs=true → tabMode=NAMESPACE，只搬一次。
            if (config.rbip.migrateLegacyTabMode()) {
                configHolder.save();
            }
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
            // NOTE: Cloth Config mutates the config object in place on save, so
            // config and event.config() are the same object here — comparing
            // them cannot detect a change.  Track the last-seen unlockAll value
            // explicitly and diff against that.
            appContext.events().subscribe(ConfigEventBus.ConfigChanged.class, event -> {
                boolean unlockChanged = lastUnlockAllValue != event.config().unlockAll;
                BrbeLogger.log("BRBE", "ConfigChanged: unlockChanged={} old={} new={}", unlockChanged, lastUnlockAllValue, event.config().unlockAll);
                lastUnlockAllValue = event.config().unlockAll;
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

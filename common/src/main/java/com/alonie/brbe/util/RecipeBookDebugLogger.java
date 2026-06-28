package com.alonie.brbe.util;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;
import java.util.Locale;

/**
 * Structured debug logger for recipe book diagnostics.
 *
 * <p>Logs complete recipe loading state at every {@code updateCollections}
 * cycle, RBIP creative-tab filtering, pipeline stages, and cache operations.
 * All output is tagged with {@code [BRBE-DEBUG]} so it can be filtered in logs.
 *
 * <p>Toggle via {@link #enabled}.  Defaults to {@code true} for diagnostic
 * builds; set to {@code false} before release.
 */
public final class RecipeBookDebugLogger {

    /** Master toggle — set to {@code false} to silence all debug output. */
    public static boolean enabled = true;

    /** Log collection contents details (can be very noisy). */
    public static boolean verboseCollections = false;

    // ── Rate limiting ──────────────────────────────────────────

    private static long lastDumpNanos;
    private static int dumpCount;
    private static final long MIN_DUMP_INTERVAL_MS = 250;

    private RecipeBookDebugLogger() {}

    // ══════════════════════════════════════════════════════════
    // Pipeline lifecycle
    // ══════════════════════════════════════════════════════════

    /** Called at the very start of updateCollections. */
    public static void onUpdateCollectionsStart(
            String screenName, boolean resetPageNumber, int tabOrdinal,
            String tabName, Object rbipVariant, String searchText) {
        if (!enabled) return;
        BetterRecipeBook.LOGGER.info(
                "[BRBE-DEBUG] ══ updateCollections START ══ screen={} reset={} tab=[{}]{} " +
                "rbip={} search=\"{}\"",
                screenName, resetPageNumber, tabOrdinal,
                (tabName != null ? " " + tabName : ""),
                (rbipVariant != null ? "active(" + rbipVariant + ")" : "none"),
                (searchText != null && !searchText.isEmpty() ? searchText : ""));
    }

    /** Called when RBIP creative tab filtering intercepts getCollection. */
    public static void onRbipFilterCollections(
            String searchCategory, int totalBase, int matched, boolean activeTabNonNull) {
        if (!enabled) return;
        BetterRecipeBook.LOGGER.info(
                "[BRBE-DEBUG] RBIP filter: searchCat={} base={} → matched={} activeTab={}",
                searchCategory, totalBase, matched, activeTabNonNull);
    }

    /** Called before data marking (forEach redirect). */
    public static void onDataMarkingStart(
            int collectionCount, long slotHash, boolean cacheHit, boolean inventoryChanged) {
        if (!enabled) return;
        BetterRecipeBook.LOGGER.info(
                "[BRBE-DEBUG] Data marking: collections={} slotHash={} cacheHit={} invChanged={}",
                collectionCount, slotHash, cacheHit, inventoryChanged);
    }

    /** Called after partial marking. */
    public static void onPartialMarkingDone(
            int totalCollections, int partialCollections, int partialRecipes,
            int incompatibleCollections, int incompatibleRecipes) {
        if (!enabled) return;
        BetterRecipeBook.LOGGER.info(
                "[BRBE-DEBUG] Partial marking done: {} collections, {} have partials ({} recipes), " +
                "{} incompatible ({} recipes)",
                totalCollections, partialCollections, partialRecipes,
                incompatibleCollections, incompatibleRecipes);
    }

    /** Called at each pipeline stage. */
    public static void onPipelineStage(String stage, int inputCount, int outputCount) {
        if (!enabled) return;
        BetterRecipeBook.LOGGER.info(
                "[BRBE-DEBUG] Pipeline [{}]: {} → {} collections",
                stage, inputCount, outputCount);
    }

    /** Called when pipeline finishes. */
    public static void onPipelineDone(int finalCount, int pageNumber, boolean cached) {
        if (!enabled) return;
        BetterRecipeBook.LOGGER.info(
                "[BRBE-DEBUG] Pipeline DONE: {} collections → page {}, cached={}",
                finalCount, pageNumber, cached);
    }

    /** Called when BookStateCache is hit or miss. */
    public static void onCacheAccess(boolean hit, Class<?> screenClass, long slotHash,
                                      Object variant, int resultCount) {
        if (!enabled) return;
        BetterRecipeBook.LOGGER.info(
                "[BRBE-DEBUG] Cache {}: screen={} slotHash={} variant={} resultCount={}",
                hit ? "HIT" : "MISS", screenClass.getSimpleName(), slotHash,
                (variant != null ? variant.hashCode() : "none"), resultCount);
    }

    // ══════════════════════════════════════════════════════════
    // Collection dump
    // ══════════════════════════════════════════════════════════

    /** Dump a summary of recipe collections (counts by category + craftable status). */
    public static void dumpCollectionSummary(
            String label, List<RecipeCollection> collections, boolean isFiltering) {
        if (!enabled) return;

        int craftable = 0;
        int partial = 0;
        int uncraftable = 0;
        int emptyRecipes = 0;
        int totalRecipes = 0;
        int pinned = 0;

        for (RecipeCollection c : collections) {
            List<RecipeHolder<?>> recipes = c.getRecipes();
            totalRecipes += recipes.size();
            if (recipes.isEmpty()) emptyRecipes++;

            if (c.hasCraftable()) craftable++;
            else if (PartialCraftingUtil.hasPartialMaterials(c)) partial++;
            else uncraftable++;

            if (BetterRecipeBook.config.enablePinning
                    && BetterRecipeBook.pinnedRecipeManager.has(
                        com.alonie.brbe.generic.pins.PinnableRecipeCollection.of(c))) {
                pinned++;
            }
        }

        BetterRecipeBook.LOGGER.info(
                "[BRBE-DEBUG] {}: {} total | craftable={} partial={} uncraftable={} " +
                "emptyRecipeColls={} totalRecipes={} pinned={} filtering={}",
                label, collections.size(), craftable, partial, uncraftable,
                emptyRecipes, totalRecipes, pinned, isFiltering);
    }

    /** Dump detailed collection info (only when verboseCollections is true). */
    public static void dumpCollectionDetails(
            String label, List<RecipeCollection> collections) {
        if (!enabled || !verboseCollections || collections.isEmpty()) return;

        // Rate-limit detailed dumps to avoid log spam
        long now = System.nanoTime();
        if (dumpCount > 0 && (now - lastDumpNanos) < MIN_DUMP_INTERVAL_MS * 1_000_000L) {
            return;
        }
        lastDumpNanos = now;
        dumpCount++;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT,
                "[BRBE-DEBUG] %s detail (%d collections, dump #%d):\n",
                label, collections.size(), dumpCount));

        int limit = Math.min(collections.size(), verboseCollections ? 40 : 10);
        for (int i = 0; i < limit; i++) {
            RecipeCollection c = collections.get(i);
            List<RecipeHolder<?>> recipes = c.getRecipes();
            String result = "?";
            if (!recipes.isEmpty()) {
                RecipeHolder<?> first = recipes.get(0);
                ItemStack stack = first.value().getResultItem(
                        net.minecraft.client.Minecraft.getInstance().level.registryAccess());
                result = stack.isEmpty() ? "(air)" : stack.getHoverName().getString();
            }
            sb.append(String.format(Locale.ROOT,
                    "  [%d] recipes=%d result=%s craftable=%s partial=%s\n",
                    i, recipes.size(), result,
                    c.hasCraftable(),
                    PartialCraftingUtil.hasPartialMaterials(c)));
        }
        if (collections.size() > limit) {
            sb.append(String.format(Locale.ROOT, "  ... and %d more\n",
                    collections.size() - limit));
        }
        BetterRecipeBook.LOGGER.info(sb.toString());
    }

    // ══════════════════════════════════════════════════════════
    // RBIP diagnostics
    // ══════════════════════════════════════════════════════════

    /** Called when RBIP creative tabs are built. */
    public static void onRbipTabsBuilt(int tabCount, int craftingCount) {
        if (!enabled) return;
        BetterRecipeBook.LOGGER.info(
                "[BRBE-DEBUG] RBIP tabs built: {} creative tabs, {} in CRAFTING_LIST",
                tabCount, craftingCount);
    }

    /** Called during RBIP incremental filtering. */
    public static void onRbipFilterProgress(int processed, int total, int removed) {
        if (!enabled) return;
        BetterRecipeBook.LOGGER.info(
                "[BRBE-DEBUG] RBIP incremental filter: {}/{} processed, {} removed so far",
                processed, total, removed);
    }

    /** Called when RBIP creative tab is selected. */
    public static void onRbipTabSelected(String tabName, String furnaceType) {
        if (!enabled) return;
        BetterRecipeBook.LOGGER.info(
                "[BRBE-DEBUG] RBIP tab selected: \"{}\" furnace={}",
                tabName, (furnaceType != null ? furnaceType : "none"));
    }

    /** Called when RBIP init completes. */
    public static void onRbipInitComplete(int tabCount, int itemMappingCount, boolean success) {
        if (!enabled) return;
        BetterRecipeBook.LOGGER.info(
                "[BRBE-DEBUG] RBIP init: {} tabs, {} item mappings, success={}",
                tabCount, itemMappingCount, success);
    }

    // ══════════════════════════════════════════════════════════
    // Search / filter diagnostics
    // ══════════════════════════════════════════════════════════

    /** Called when search text is processed. */
    public static void onSearchProcessed(
            String rawText, boolean isAdvanced, String parsedSummary) {
        if (!enabled) return;
        BetterRecipeBook.LOGGER.info(
                "[BRBE-DEBUG] Search: raw=\"{}\" advanced={} parsed={}",
                rawText, isAdvanced, parsedSummary);
    }

    /** Called when the filter toggle changes state. */
    public static void onFilterToggle(boolean isFiltering) {
        if (!enabled) return;
        BetterRecipeBook.LOGGER.info(
                "[BRBE-DEBUG] Filter toggle: {}", isFiltering ? "ON (show craftable only)" : "OFF (show all)");
    }

    // ══════════════════════════════════════════════════════════
    // Config dump
    // ══════════════════════════════════════════════════════════

    /** Dump relevant config state on first call. */
    private static boolean configDumped;

    public static void dumpConfigOnce() {
        if (!enabled || configDumped) return;
        configDumped = true;

        BetterRecipeBook.LOGGER.info(
                "[BRBE-DEBUG] ══ Config ══ " +
                "partialCrafting={} partialMarking={} noGrouped={} onHover={} " +
                "enablePinning={} instantCraft={} showAllSurvival={} keepCentered={} " +
                "scrolling={} rbip={}",
                BetterRecipeBook.config.partialCraftingEnabled,
                BetterRecipeBook.config.partialMarkingEnabled,
                BetterRecipeBook.config.alternativeRecipes.noGrouped,
                BetterRecipeBook.config.alternativeRecipes.onHover,
                BetterRecipeBook.config.enablePinning,
                BetterRecipeBook.config.instantCraft.enabled,
                BetterRecipeBook.config.showAllRecipesInSurvival,
                BetterRecipeBook.config.keepCentered,
                BetterRecipeBook.config.scrolling.enableScrolling,
                (BetterRecipeBook.config.rbip != null
                        ? BetterRecipeBook.config.rbip.enableRecipeBookIsPain : "null"));
    }
}

package com.alonie.brbe.cache;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.SlotDisplay;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Local vanilla recipe cache that supplements server-provided recipes.
 *
 * Recipes are loaded from the classpath (Minecraft JAR) at startup by
 * {@link VanillaRecipeLoader} — no file I/O, no singleplayer capture needed.
 *
 * <h3>Complement mode</h3>
 * Instead of a fixed threshold, the cache uses <b>complement injection</b>:
 * only local entries whose result item is NOT already covered by the server
 * are injected.  This handles:
 * <ul>
 *   <li>Servers that send zero recipes → all local recipes injected</li>
 *   <li>Servers that send a partial set (e.g. 60 of 1200) → missing recipes filled in</li>
 *   <li>Servers that send a complete set → nothing injected</li>
 *   <li>Servers with custom recipes → custom entries are preserved</li>
 * </ul>
 *
 * <h3>Debugging</h3>
 * Set the system property {@code brbe.cache.debug=true} to enable verbose
 * complement logs.  When enabled, the first complement of each session
 * prints a full status report including sample recipe keys and a
 * per-category breakdown.  Call {@link #dumpStatus()} at any time to
 * re-print the report.
 *
 * <h3>ID-space partition</h3>
 * Cache entries use <b>negative</b> {@link RecipeDisplayId} values (starting
 * at -1, decrementing).  Server-assigned IDs are always non-negative.  This
 * creates a hard partition: {@code isLocalRecipe(id)} is simply
 * {@code id.index() &lt; 0}, with zero collision risk regardless of what IDs
 * the server sends later.
 */
public final class VanillaRecipeCache {

    // ---- Debug ----

    /** Set with {@code -Dbrbe.cache.debug=true} to enable verbose complement logging. */
    private static final boolean DEBUG = Boolean.getBoolean("brbe.cache.debug");

    /** Max recipe keys to log in debug output (avoid log spam). */
    private static final int DEBUG_SAMPLE_SIZE = 15;

    // ---- State ----

    /** All cached vanilla recipe entries, loaded at init from classpath. */
    private static final Map<String, CacheableRecipeDisplayEntry> cache = new LinkedHashMap<>();

    /** Snapshot of the last complement operation for debugging. */
    private static final List<String> lastComplemented = new ArrayList<>();
    private static final List<String> lastSkipped = new ArrayList<>();
    private static final List<String> lastServerItems = new ArrayList<>();
    private static final Map<String, int[]> lastCategoryBreakdown = new LinkedHashMap<>(); // cat -> [injected, skipped]

    private VanillaRecipeCache() {}

    // ---- Lifecycle ----

    /** Called once from BetterRecipeBook.init(). Loads vanilla recipes from classpath. */
    public static void init() {
        cache.clear();
        List<CacheableRecipeDisplayEntry> loaded = VanillaRecipeLoader.loadAll();
        for (CacheableRecipeDisplayEntry entry : loaded) {
            if (entry != null && entry.recipeKey() != null) {
                cache.put(entry.recipeKey(), entry);
            }
        }
        BetterRecipeBook.LOGGER.info("[BRBE-CACHE] init loaded {} vanilla recipes from classpath",
                cache.size());

        if (DEBUG) {
            // Print category distribution at init time
            Map<String, Long> byCategory = cache.values().stream()
                    .collect(Collectors.groupingBy(
                            e -> e.categoryName() != null ? e.categoryName() : "null",
                            LinkedHashMap::new, Collectors.counting()));
            BetterRecipeBook.LOGGER.info("[BRBE-CACHE] DEBUG cache by category: {}", byCategory);
        }
    }

    /** Called from MinecraftMixin on disconnect. Resets session-only state. */
    public static void clear() {
        BetterRecipeBook.LOGGER.info("[BRBE-CACHE] session cleared");
        if (DEBUG) {
            lastComplemented.clear();
            lastSkipped.clear();
            lastServerItems.clear();
            lastCategoryBreakdown.clear();
        }
    }

    // ---- Detection and orchestration ----

    /**
     * Called before ClientRecipeBook.rebuildCollections().
     * Complements the server-provided recipe set with locally-cached vanilla
     * entries for any result items the server didn't cover.
     */
    public static void detectAndInject(ClientRecipeBook recipeBook,
                                        Map<RecipeDisplayId, RecipeDisplayEntry> known) {
        if (cache.isEmpty()) return;

        int serverCount = (int) known.keySet().stream().filter(id -> id.index() >= 0).count();
        BetterRecipeBook.LOGGER.info("[BRBE-CACHE] pre-rebuild known count: {} (server: {})",
                known.size(), serverCount);

        // Purge any previously-injected cache entries (negative IDs) before
        // re-injecting.  Server IDs are always ≥ 0.
        known.keySet().removeIf(id -> id.index() < 0);

        // Complement: inject local entries for result items the server didn't send
        complement(known);
    }

    // ---- Complement ----

    /**
     * Injects locally-cached entries for any result item not already covered
     * by the server-provided recipe set.
     */
    private static void complement(Map<RecipeDisplayId, RecipeDisplayEntry> known) {
        // Clear debug snapshots from previous run
        if (DEBUG) {
            lastComplemented.clear();
            lastSkipped.clear();
            lastServerItems.clear();
            lastCategoryBreakdown.clear();
        }

        // 1. Collect result item IDs from server-provided entries
        Set<String> serverResultItems = new HashSet<>();
        for (RecipeDisplayEntry entry : known.values()) {
            String resultId = extractResultItemId(entry.display().result());
            if (resultId != null) {
                serverResultItems.add(resultId);
                if (DEBUG && lastServerItems.size() < DEBUG_SAMPLE_SIZE) {
                    lastServerItems.add(resultId);
                }
            }
        }
        BetterRecipeBook.LOGGER.info("[BRBE-CACHE] server covers {} unique result items",
                serverResultItems.size());

        // 2. Inject local entries whose result item is NOT already covered
        int nextId = -1; // cache IDs are negative — never collide with server IDs (≥ 0)
        int injectedCount = 0;
        int skippedCount = 0;

        for (CacheableRecipeDisplayEntry cEntry : cache.values()) {
            try {
                String cat = cEntry.categoryName() != null ? cEntry.categoryName() : "unknown";
                if (cEntry.resultItem() != null && serverResultItems.contains(cEntry.resultItem())) {
                    skippedCount++;
                    if (DEBUG) {
                        if (lastSkipped.size() < DEBUG_SAMPLE_SIZE) {
                            lastSkipped.add(cEntry.recipeKey() + " → " + cEntry.resultItem());
                        }
                        lastCategoryBreakdown.computeIfAbsent(cat, k -> new int[2])[1]++;
                    }
                    continue; // server already covers this result item
                }

                RecipeDisplayId newId = new RecipeDisplayId(nextId--);
                RecipeDisplayEntry entry = cEntry.toEntry(newId);
                if (entry != null) {
                    known.put(newId, entry);
                    injectedCount++;
                    if (DEBUG) {
                        if (lastComplemented.size() < DEBUG_SAMPLE_SIZE) {
                            lastComplemented.add(cEntry.recipeKey() + " → " + cEntry.resultItem());
                        }
                        lastCategoryBreakdown.computeIfAbsent(cat, k -> new int[2])[0]++;
                    }
                }
            } catch (Exception e) {
                BetterRecipeBook.LOGGER.warn(
                        "[BRBE-CACHE] failed to complement entry {}: {}",
                        cEntry.recipeKey(), e.getMessage());
            }
        }

        BetterRecipeBook.LOGGER.info(
                "[BRBE-CACHE] complement: {} injected, {} skipped (known now {})",
                injectedCount, skippedCount, known.size());

        // First complement of session → dump full report when debugging
        if (DEBUG) {
            dumpStatus();
        }
    }

    // ---- Debug ----

    /**
     * Prints a full complement status report to the log.
     * Called automatically after the first complement when debug is enabled;
     * can also be called manually (e.g. from a keybind or command hook).
     */
    public static void dumpStatus() {
        BetterRecipeBook.LOGGER.info("========== [BRBE-CACHE] STATUS REPORT ==========");
        BetterRecipeBook.LOGGER.info("  Cache size : {} vanilla recipes", cache.size());
        BetterRecipeBook.LOGGER.info("  Debug mode : {}", DEBUG ? "ON" : "OFF (set -Dbrbe.cache.debug=true)");

        if (lastServerItems.isEmpty() && lastComplemented.isEmpty() && lastSkipped.isEmpty()) {
            BetterRecipeBook.LOGGER.info("  (no complement has run this session)");
        } else {
            BetterRecipeBook.LOGGER.info("  Server items (sample {}): {}",
                    lastServerItems.size(), lastServerItems);
            BetterRecipeBook.LOGGER.info("  Complemented (sample {}): {}",
                    lastComplemented.size(), lastComplemented);
            BetterRecipeBook.LOGGER.info("  Skipped (sample {}): {}",
                    lastSkipped.size(), lastSkipped);

            if (!lastCategoryBreakdown.isEmpty()) {
                BetterRecipeBook.LOGGER.info("  By category (injected / skipped):");
                lastCategoryBreakdown.forEach((cat, counts) ->
                        BetterRecipeBook.LOGGER.info("    {} : {} / {}", cat, counts[0], counts[1]));
            }
        }

        // Quick health check
        if (!cache.isEmpty() && lastComplemented.isEmpty() && lastSkipped.isEmpty()) {
            BetterRecipeBook.LOGGER.info("  ⚠ No complement activity — server may have sent all recipes, or module didn't run yet");
        }
        BetterRecipeBook.LOGGER.info("====================================================");
    }

    // ---- Helpers ----

    /**
     * Extracts the registry item ID from a SlotDisplay result.
     * Handles {@link SlotDisplay.ItemSlotDisplay} and
     * {@link SlotDisplay.ItemStackSlotDisplay}; returns {@code null}
     * for tag, composite, and empty displays (which can't resolve to
     * a single item).
     */
    static String extractResultItemId(SlotDisplay slot) {
        if (slot instanceof SlotDisplay.ItemSlotDisplay itemSlot) {
            return BuiltInRegistries.ITEM.getKey(itemSlot.item().value()).toString();
        }
        if (slot instanceof SlotDisplay.ItemStackSlotDisplay stackSlot) {
            return BuiltInRegistries.ITEM.getKey(stackSlot.stack().item().value()).toString();
        }
        // TagSlotDisplay, Composite, Empty — no single item ID
        return null;
    }

    // ---- Queries ----

    /**
     * Returns true if {@code id} was generated by the local cache rather than
     * the server.  Cache entries always use negative indices; server IDs are
     * always non-negative, so this is a zero-collision check.
     */
    public static boolean isLocalRecipe(RecipeDisplayId id) {
        return id.index() < 0;
    }

    public static boolean hasEntries() {
        return !cache.isEmpty();
    }

    public static int cacheSize() { return cache.size(); }
}

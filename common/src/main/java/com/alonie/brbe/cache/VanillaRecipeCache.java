package com.alonie.brbe.cache;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Local vanilla recipe cache that supplements server-provided recipes.
 *
 * Recipes are loaded from the classpath (Minecraft JAR) at startup by
 * {@link VanillaRecipeLoader} — no file I/O, no singleplayer capture needed.
 *
 * <h3>Always-inject mode</h3>
 * Every time {@link ClientRecipeBook#rebuildCollections()} runs, cached
 * entries are unconditionally injected into the {@code known} map (minus
 * entries that resolve to air).  Because cache entries use <b>negative</b>
 * {@link RecipeDisplayId} values and server entries use non-negative IDs,
 * there is zero collision risk.
 *
 * <p>This means the recipe book always contains ALL vanilla recipes
 * regardless of how many the server sends — the user sees the complete
 * set even on servers that only send a partial recipe book (e.g. Hypixel).
 *
 * <h3>ID-space partition</h3>
 * Cache entries use <b>negative</b> {@link RecipeDisplayId} values (starting
 * at -1, decrementing).  Server-assigned IDs are always non-negative.  This
 * creates a hard partition: {@code isLocalRecipe(id)} is simply
 * {@code id.index() &lt; 0}, with zero collision risk regardless of what IDs
 * the server sends later.
 */
public final class VanillaRecipeCache {

    /** Max recipe keys to log per category (avoid log spam). */
    private static final int SAMPLE_SIZE = 15;

    // ---- State ----

    /** All cached vanilla recipe entries, loaded at init from classpath. */
    private static final Map<String, CacheableRecipeDisplayEntry> cache = new LinkedHashMap<>();

    /** Snapshot of the last injection for status reporting. */
    private static final List<String> lastInjected = new ArrayList<>();
    private static final List<String> lastFiltered = new ArrayList<>();
    private static final Map<String, Integer> lastCategoryBreakdown = new LinkedHashMap<>();
    private static int lastServerCount = 0;

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
        Map<String, Long> byCategory = cache.values().stream()
                .collect(Collectors.groupingBy(
                        e -> e.categoryName() != null ? e.categoryName() : "null",
                        LinkedHashMap::new, Collectors.counting()));
        BetterRecipeBook.LOGGER.info("[BRBE-CACHE] cache by category: {}", byCategory);
    }

    /** Called from MinecraftMixin on disconnect. Resets session-only state. */
    public static void clear() {
        BetterRecipeBook.LOGGER.info("[BRBE-CACHE] session cleared");
        lastInjected.clear();
        lastFiltered.clear();
        lastCategoryBreakdown.clear();
        lastServerCount = 0;
    }

    // ---- Detection and injection ----

    /**
     * Called before ClientRecipeBook.rebuildCollections().
     * Purges old cache entries (negative IDs) from previous injection,
     * then re-injects all currently-valid cached entries.
     */
    public static void detectAndInject(ClientRecipeBook recipeBook,
                                        Map<RecipeDisplayId, RecipeDisplayEntry> known) {
        if (cache.isEmpty()) return;

        lastServerCount = (int) known.keySet().stream().filter(id -> id.index() >= 0).count();
        BetterRecipeBook.LOGGER.info("[BRBE-CACHE] pre-rebuild known count: {} (server: {})",
                known.size(), lastServerCount);

        // Purge any previously-injected cache entries (negative IDs) before
        // re-injecting.  Server IDs are always ≥ 0.
        known.keySet().removeIf(id -> id.index() < 0);

        // Inject all valid cached entries unconditionally
        injectAll(known);
    }

    // ---- Injection ----

    /**
     * Injects ALL cached entries that resolve to valid items into the known map.
     * Entries that produce air (empty result stacks) are filtered out.
     */
    private static void injectAll(Map<RecipeDisplayId, RecipeDisplayEntry> known) {
        // Clear snapshots
        lastInjected.clear();
        lastFiltered.clear();
        lastCategoryBreakdown.clear();

        int nextId = -1;
        int injectedCount = 0;
        int filteredCount = 0;

        for (CacheableRecipeDisplayEntry cEntry : cache.values()) {
            try {
                String cat = cEntry.categoryName() != null ? cEntry.categoryName() : "unknown";

                RecipeDisplayId newId = new RecipeDisplayId(nextId--);
                RecipeDisplayEntry entry = cEntry.toEntry(newId);
                if (entry == null) {
                    filteredCount++;
                    continue;
                }

                // Pre-validate: skip entries whose result items don't resolve
                // These are recipe types we can't reconstruct (e.g. smithing_trim)
                List<ItemStack> results;
                try {
                    results = entry.resultItems(null);
                } catch (Exception resEx) {
                    results = List.of();
                }
                if (results.isEmpty() || results.stream().allMatch(
                        s -> s == null || s.isEmpty())) {
                    filteredCount++;
                    if (lastFiltered.size() < SAMPLE_SIZE) {
                        lastFiltered.add(cEntry.recipeKey() + " → " + cEntry.resultItem());
                    }
                    continue;
                }

                known.put(newId, entry);
                injectedCount++;
                if (lastInjected.size() < SAMPLE_SIZE) {
                    lastInjected.add(cEntry.recipeKey() + " → " + cEntry.resultItem());
                }
                lastCategoryBreakdown.merge(cat, 1, Integer::sum);
            } catch (Exception e) {
                BetterRecipeBook.LOGGER.warn("[BRBE-CACHE] failed to inject entry {}: {}",
                        cEntry.recipeKey(), e.getMessage());
            }
        }

        BetterRecipeBook.LOGGER.info(
                "[BRBE-CACHE] injected: {} cached, {} filtered (known now {}, server count {})",
                injectedCount, filteredCount, known.size(), lastServerCount);

        if (filteredCount > 0) {
            BetterRecipeBook.LOGGER.warn("[BRBE-CACHE] filtered air entries (first {}): {}",
                    Math.min(SAMPLE_SIZE, lastFiltered.size()), lastFiltered);
        }

        dumpStatus();
    }

    // ---- Status reporting ----

    public static void dumpStatus() {
        BetterRecipeBook.LOGGER.info("========== [BRBE-CACHE] STATUS REPORT ==========");
        BetterRecipeBook.LOGGER.info("  Cache size: {}, server recipes in known: {}",
                cache.size(), lastServerCount);

        if (!lastInjected.isEmpty()) {
            BetterRecipeBook.LOGGER.info("  Injected (sample {}): {}", lastInjected.size(), lastInjected);
        }
        if (!lastCategoryBreakdown.isEmpty()) {
            BetterRecipeBook.LOGGER.info("  By category (injected): {}", lastCategoryBreakdown);
        }
        BetterRecipeBook.LOGGER.info("================================================");
    }

    // ---- Queries ----

    /**
     * Returns true if {@code id} was generated by the local cache rather than
     * the server.  Cache entries always use negative indices; server IDs are
     * always non-negative.
     */
    public static boolean isLocalRecipe(RecipeDisplayId id) {
        return id.index() < 0;
    }

    public static boolean hasEntries() {
        return !cache.isEmpty();
    }

    public static int cacheSize() { return cache.size(); }
}

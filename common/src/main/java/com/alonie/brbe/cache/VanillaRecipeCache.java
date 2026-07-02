package com.alonie.brbe.cache;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
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

        if (lastServerCount == 0) {
            // No server recipes: inject ALL (Hypixel, no-packet servers)
            injectEntries(known, Set.of());
        } else {
            // Server sent recipes: complement — only inject what server didn't cover
            Set<String> serverResultItems = collectServerResultItems(known);
            injectEntries(known, serverResultItems);
        }
    }

    /**
     * Collects category:itemId pairs from server-provided entries (positive IDs).
     *
     * <p>Each pair is {@code "categoryName:itemId"} (e.g.
     * {@code "furnace_food:minecraft:charcoal"}).  The category is normalised
     * to the short form used by cache entries (without the {@code minecraft:}
     * namespace prefix).  Matching on category+item prevents cross-station
     * shadowing — a furnace recipe for charcoal will not hide the campfire
     * recipe for the same item.
     */
    private static Set<String> collectServerResultItems(Map<RecipeDisplayId, RecipeDisplayEntry> known) {
        Set<String> keys = new HashSet<>();
        for (RecipeDisplayEntry entry : known.values()) {
            String rid = extractResultItemId(entry.display().result());
            if (rid != null) {
                String catStr = entry.category() != null
                        ? BuiltInRegistries.RECIPE_BOOK_CATEGORY.getKey(entry.category()).toString()
                        : "unknown";
                if (catStr.startsWith("minecraft:"))
                    catStr = catStr.substring("minecraft:".length());
                keys.add(catStr + ":" + rid);
            }
        }
        BetterRecipeBook.LOGGER.info("[BRBE-CACHE] server covers {} unique category:item entries",
                keys.size());
        return keys;
    }

    // ---- Injection ----

    /**
     * Injects cached entries into the known map.
     *
     * @param serverResultItems category:itemId pairs already covered by the server
     *        (e.g. "furnace_food:minecraft:charcoal").  Cache entries whose
     *        category+resultItem matches a pair in this set are skipped.
     *        Pass an empty set to inject all valid entries.
     */
    private static void injectEntries(Map<RecipeDisplayId, RecipeDisplayEntry> known,
                                       Set<String> serverResultItems) {
        // Clear snapshots
        lastInjected.clear();
        lastFiltered.clear();
        lastCategoryBreakdown.clear();

        int nextId = -1;
        int injectedCount = 0;
        int skippedCount = 0;
        int filteredCount = 0;
        boolean complementMode = !serverResultItems.isEmpty();

        for (CacheableRecipeDisplayEntry cEntry : cache.values()) {
            try {
                // In complement mode, skip entries already covered by the server.
                // Match on category:itemId to prevent cross-station shadowing
                // (e.g. a furnace recipe for charcoal must not hide the campfire recipe).
                if (complementMode && cEntry.resultItem() != null) {
                    String matchKey = (cEntry.categoryName() != null ? cEntry.categoryName() : "unknown")
                            + ":" + cEntry.resultItem();
                    if (serverResultItems.contains(matchKey)) {
                        skippedCount++;
                        continue;
                    }
                }

                String cat = cEntry.categoryName() != null ? cEntry.categoryName() : "unknown";

                RecipeDisplayId newId = new RecipeDisplayId(nextId--);
                RecipeDisplayEntry entry = cEntry.toEntry(newId);
                if (entry == null) {
                    filteredCount++;
                    continue;
                }

                // Pre-validate: skip entries whose result items don't resolve.
                // Entries with null resultItem() — e.g. smithing trim recipes whose
                // result is dynamically composed (template + material) — cannot be
                // validated via resultItems(null), so we inject them unconditionally.
                if (cEntry.resultItem() != null) {
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

        String mode = complementMode ? "complement" : "all";
        BetterRecipeBook.LOGGER.info(
                "[BRBE-CACHE] injected ({}): {} cached, {} skipped, {} filtered (known now {})",
                mode, injectedCount, skippedCount, filteredCount, known.size());

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

    // ---- Helpers ----

    /**
     * Extracts the registry item ID from a SlotDisplay result.
     * Handles {@link SlotDisplay.ItemSlotDisplay} and
     * {@link SlotDisplay.ItemStackSlotDisplay}; returns {@code null}
     * for tag, composite, and empty displays.
     */
    static String extractResultItemId(SlotDisplay slot) {
        if (slot instanceof SlotDisplay.ItemSlotDisplay itemSlot) {
            return BuiltInRegistries.ITEM.getKey(itemSlot.item().value()).toString();
        }
        if (slot instanceof SlotDisplay.ItemStackSlotDisplay stackSlot) {
            return BuiltInRegistries.ITEM.getKey(stackSlot.stack().item().value()).toString();
        }
        return null;
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

    // ---- Full dump for diff debugging ----

    /**
     * Dump every entry in known to the log in a compact, diffable format:
     * {@code [BRBE-DUMP] category source[id] resultItem}
     *
     * <p>Always-on.  Entries are sorted by category then result item so two
     * logs can be compared with standard diff tools to identify missing recipes.
     */
    public static void dumpAllKnown(Map<RecipeDisplayId, RecipeDisplayEntry> known) {
        // Always-on: lightweight structured log for diff debugging

        List<java.util.Map.Entry<RecipeDisplayId, RecipeDisplayEntry>> sorted =
                new ArrayList<>(known.entrySet());
        sorted.sort((a, b) -> {
            String catA = categoryKey(a.getValue().category());
            String catB = categoryKey(b.getValue().category());
            int cmp = catA.compareTo(catB);
            if (cmp != 0) return cmp;
            String itemA = java.util.Objects.toString(
                    extractResultItemId(a.getValue().display().result()), "");
            String itemB = java.util.Objects.toString(
                    extractResultItemId(b.getValue().display().result()), "");
            return itemA.compareTo(itemB);
        });

        BetterRecipeBook.LOGGER.info("[BRBE-DUMP] === BEGIN {} entries ===", known.size());
        for (var entry : sorted) {
            RecipeDisplayId id = entry.getKey();
            RecipeDisplayEntry val = entry.getValue();
            String source = id.index() < 0 ? "cache" : "server";
            String cat = categoryKey(val.category());
            String result = extractResultItemId(val.display().result());
            if (result == null) result = "<no-item>";
            BetterRecipeBook.LOGGER.info("[BRBE-DUMP] {} {}[{}] {}",
                    cat, source, id.index(), result);
        }
        BetterRecipeBook.LOGGER.info("[BRBE-DUMP] === END {} entries ===", known.size());
    }

    /**
     * Convert a RecipeBookCategory to a short, namespace-stripped key for
     * log output and comparison.
     */
    private static String categoryKey(net.minecraft.world.item.crafting.RecipeBookCategory category) {
        if (category == null) return "unknown";
        String key = BuiltInRegistries.RECIPE_BOOK_CATEGORY.getKey(category).toString();
        if (key.startsWith("minecraft:")) return key.substring("minecraft:".length());
        return key;
    }
}

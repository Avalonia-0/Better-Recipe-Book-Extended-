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
 * <h3>Dual-mode injection</h3>
 * <ul>
 *   <li><b>No server recipes</b> (known has zero server entries): inject ALL
 *       valid cached entries.  Covers servers that never send recipe packets
 *       (e.g. Hypixel).</li>
 *   <li><b>Server recipes present</b>: complement mode — only inject cache
 *       entries whose result item is NOT already covered by the server.
 *       Eliminates duplicates in singleplayer while filling gaps on
 *       partial-recipe servers.</li>
 * </ul>
 *
 * <h3>ID-space partition</h3>
 * Cache entries use <b>negative</b> {@link RecipeDisplayId} values (starting
 * at -1, decrementing).  Server-assigned IDs are always non-negative.  This
 * creates a hard partition: {@code isLocalRecipe(id)} is simply
 * {@code id.index() &lt; 0}, with zero collision risk regardless of what IDs
 * the server sends later.
 */
public final class VanillaRecipeCache {

    private static final int SAMPLE_SIZE = 15;

    private static final Map<String, CacheableRecipeDisplayEntry> cache = new LinkedHashMap<>();
    private static final List<String> lastInjected = new ArrayList<>();
    private static final List<String> lastFiltered = new ArrayList<>();
    private static final Map<String, Integer> lastCategoryBreakdown = new LinkedHashMap<>();
    private static int lastServerCount = 0;

    private VanillaRecipeCache() {}

    public static void init() {
        cache.clear();
        List<CacheableRecipeDisplayEntry> loaded = VanillaRecipeLoader.loadAll();
        for (CacheableRecipeDisplayEntry entry : loaded) {
            if (entry != null && entry.recipeKey() != null) {
                cache.put(entry.recipeKey(), entry);
            }
        }
        BetterRecipeBook.LOGGER.info("[BRBE-CACHE] init loaded {} vanilla recipes from classpath", cache.size());
        Map<String, Long> byCategory = cache.values().stream()
                .collect(Collectors.groupingBy(
                        e -> e.categoryName() != null ? e.categoryName() : "null",
                        LinkedHashMap::new, Collectors.counting()));
        BetterRecipeBook.LOGGER.info("[BRBE-CACHE] cache by category: {}", byCategory);
    }

    public static void clear() {
        BetterRecipeBook.LOGGER.info("[BRBE-CACHE] session cleared");
        lastInjected.clear();
        lastFiltered.clear();
        lastCategoryBreakdown.clear();
        lastServerCount = 0;
    }

    public static void detectAndInject(ClientRecipeBook recipeBook,
                                        Map<RecipeDisplayId, RecipeDisplayEntry> known) {
        if (cache.isEmpty()) return;
        lastServerCount = (int) known.keySet().stream().filter(id -> id.index() >= 0).count();
        BetterRecipeBook.LOGGER.info("[BRBE-CACHE] pre-rebuild known count: {} (server: {})",
                known.size(), lastServerCount);
        known.keySet().removeIf(id -> id.index() < 0);
        if (lastServerCount == 0) {
            injectEntries(known, Set.of());
        } else {
            Set<String> serverResultItems = collectServerResultItems(known);
            injectEntries(known, serverResultItems);
        }
    }

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

    private static void injectEntries(Map<RecipeDisplayId, RecipeDisplayEntry> known,
                                       Set<String> serverResultItems) {
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
                if (entry == null) { filteredCount++; continue; }
                List<ItemStack> results;
                if (cEntry.resultItem() != null) {
                    try { results = entry.resultItems(null); }
                    catch (Exception resEx) { results = List.of(); }
                    if (results.isEmpty() || results.stream().allMatch(s -> s == null || s.isEmpty())) {
                        filteredCount++;
                        if (lastFiltered.size() < SAMPLE_SIZE)
                            lastFiltered.add(cEntry.recipeKey() + " → " + cEntry.resultItem());
                        continue;
                    }
                }
                known.put(newId, entry);
                injectedCount++;
                if (lastInjected.size() < SAMPLE_SIZE)
                    lastInjected.add(cEntry.recipeKey() + " → " + cEntry.resultItem());
                lastCategoryBreakdown.merge(cat, 1, Integer::sum);
            } catch (Exception e) {
                BetterRecipeBook.LOGGER.warn("[BRBE-CACHE] failed to inject entry {}: {}",
                        cEntry.recipeKey(), e.getMessage());
            }
        }
        String mode = complementMode ? "complement" : "all";
        BetterRecipeBook.LOGGER.info("[BRBE-CACHE] injected ({}): {} cached, {} skipped, {} filtered (known now {})",
                mode, injectedCount, skippedCount, filteredCount, known.size());
        if (filteredCount > 0)
            BetterRecipeBook.LOGGER.warn("[BRBE-CACHE] filtered air entries (first {}): {}",
                    Math.min(SAMPLE_SIZE, lastFiltered.size()), lastFiltered);
        dumpStatus();
    }

    public static void dumpStatus() {
        BetterRecipeBook.LOGGER.info("========== [BRBE-CACHE] STATUS REPORT ==========");
        BetterRecipeBook.LOGGER.info("  Cache size: {}, server recipes in known: {}",
                cache.size(), lastServerCount);
        if (!lastInjected.isEmpty())
            BetterRecipeBook.LOGGER.info("  Injected (sample {}): {}", lastInjected.size(), lastInjected);
        if (!lastCategoryBreakdown.isEmpty())
            BetterRecipeBook.LOGGER.info("  By category (injected): {}", lastCategoryBreakdown);
        BetterRecipeBook.LOGGER.info("================================================");
    }

    static String extractResultItemId(SlotDisplay slot) {
        if (slot instanceof SlotDisplay.ItemSlotDisplay itemSlot)
            return BuiltInRegistries.ITEM.getKey(itemSlot.item().value()).toString();
        if (slot instanceof SlotDisplay.ItemStackSlotDisplay stackSlot)
            return BuiltInRegistries.ITEM.getKey(stackSlot.stack().item().value()).toString();
        return null;
    }

    public static boolean isLocalRecipe(RecipeDisplayId id) { return id.index() < 0; }
    public static boolean hasEntries() { return !cache.isEmpty(); }
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

        List<Map.Entry<RecipeDisplayId, RecipeDisplayEntry>> sorted =
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

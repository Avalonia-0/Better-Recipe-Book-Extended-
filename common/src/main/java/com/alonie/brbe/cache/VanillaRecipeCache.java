package com.alonie.brbe.cache;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

import java.util.*;

/**
 * Local vanilla recipe cache that supplements server-provided recipes.
 *
 * Recipes are loaded from the classpath (Minecraft JAR) at startup by
 * {@link VanillaRecipeLoader} — no file I/O, no singleplayer capture needed.
 *
 * On recipe-sparse servers (known.size() < 50), cached entries are injected
 * into {@link ClientRecipeBook#known} before rebuildCollections().
 */
public final class VanillaRecipeCache {

    /** Maximum known recipes to consider server "sparse" and trigger injection. */
    private static final int SPARSE_THRESHOLD = 50;

    // ---- State ----

    /** All cached vanilla recipe entries, loaded at init from classpath. */
    private static final Map<String, CacheableRecipeDisplayEntry> cache = new LinkedHashMap<>();

    /** IDs injected into the current session's recipe book; cleaned up on disconnect. */
    private static final Set<RecipeDisplayId> injectedIds = new HashSet<>();

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
    }

    /** Called from MinecraftMixin on disconnect. Resets session-only state. */
    public static void clear() {
        injectedIds.clear();
        BetterRecipeBook.LOGGER.info("[BRBE-CACHE] session cleared");
    }

    // ---- Detection and orchestration ----

    /**
     * Called before ClientRecipeBook.rebuildCollections().
     * If the server looks recipe-sparse and we have a cache, inject entries.
     */
    public static void detectAndInject(ClientRecipeBook recipeBook,
                                        Map<RecipeDisplayId, RecipeDisplayEntry> known) {
        if (cache.isEmpty()) return;

        int count = known.size();
        BetterRecipeBook.LOGGER.info("[BRBE-CACHE] pre-rebuild known count: {}", count);

        if (count < SPARSE_THRESHOLD) {
            if (!injectedIds.isEmpty()) {
                for (RecipeDisplayId id : injectedIds) {
                    known.remove(id);
                }
                injectedIds.clear();
            }
            injectInto(recipeBook, known);
        }
    }

    // ---- Inject ----

    private static void injectInto(ClientRecipeBook recipeBook,
                                    Map<RecipeDisplayId, RecipeDisplayEntry> known) {
        int injectedCount = 0;
        int nextId = findMaxId(known) + 1;

        for (CacheableRecipeDisplayEntry cEntry : cache.values()) {
            try {
                RecipeDisplayId newId = new RecipeDisplayId(nextId++);
                RecipeDisplayEntry entry = cEntry.toEntry(newId);
                if (entry != null) {
                    known.put(newId, entry);
                    injectedIds.add(newId);
                    injectedCount++;
                }
            } catch (Exception e) {
                BetterRecipeBook.LOGGER.warn(
                        "[BRBE-CACHE] failed to reconstruct entry {}: {}",
                        cEntry.recipeKey(), e.getMessage());
            }
        }

        BetterRecipeBook.LOGGER.info(
                "[BRBE-CACHE] injected {} cached entries (known now {})",
                injectedCount, known.size());
    }

    private static int findMaxId(Map<RecipeDisplayId, RecipeDisplayEntry> known) {
        int max = 0;
        for (RecipeDisplayId id : known.keySet()) {
            if (id.index() > max) max = id.index();
        }
        return max;
    }

    // ---- Queries ----

    public static boolean isLocalRecipe(RecipeDisplayId id) {
        return injectedIds.contains(id);
    }

    public static boolean hasEntries() {
        return !cache.isEmpty();
    }

    public static int cacheSize() { return cache.size(); }
    public static int injectedCount() { return injectedIds.size(); }
}

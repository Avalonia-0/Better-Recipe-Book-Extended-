package com.alonie.brbe.generic.pins;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.List;

public final class PinnableRecipeCollection implements Pinnable {
    private final List<Identifier> identifiers;
    private final java.util.Set<Identifier> identifierSet;

    /**
     * Identity-keyed cache of computed pin identifiers.  A collection's
     * recipes (and therefore its pin ids) only change when the collection
     * object is recreated (rebuildCollections), so the expensive
     * SHA-1-per-recipe computation can be reused across pipeline passes.
     * Weak: entries drop when the collection is GC'd.  PinnedRecipeManager
     * also keys pins by these ids, so the cache is safe under pin changes —
     * it only memoizes the id derivation, not the pinned state.
     */
    private static final java.util.Map<RecipeCollection, PinnableRecipeCollection> CACHE =
            new java.util.WeakHashMap<>();

    private PinnableRecipeCollection(List<Identifier> identifiers) {
        this.identifiers = identifiers;
        this.identifierSet = new java.util.HashSet<>(identifiers);
    }

    public static PinnableRecipeCollection of(RecipeCollection collection) {
        PinnableRecipeCollection cached = CACHE.get(collection);
        if (cached != null) {
            return cached;
        }
        PinnableRecipeCollection created = new PinnableRecipeCollection(
                collection.getRecipes().stream()
                        .map(PinnableRecipeCollection::idFor)
                        .toList());
        CACHE.put(collection, created);
        return created;
    }

    public Collection<Identifier> identifiers() {
        return this.identifiers;
    }

    @Override
    public boolean has(Identifier identifier) {
        return this.identifierSet.contains(identifier);
    }

    /** Drop the cached id-derivation for a collection (used when its recipe
     *  set is known to have changed without object recreation). */
    public static void invalidate(RecipeCollection collection) {
        CACHE.remove(collection);
    }

    /** The stable pin id of one display entry (category | group | display).
     *  The same derivation is used by the recipe book and the query viewer, so
     *  a pin created in either place matches the entry wherever its display
     *  value is equal ({@code RecipeDisplayEntry} value semantics). */
    public static Identifier idFor(RecipeDisplayEntry entry) {
        String stableKey = entry.category() + "|" + entry.group().orElse(-1) + "|" + entry.display();
        return Identifier.fromNamespaceAndPath(BetterRecipeBook.MOD_ID, "pin/" + sha1Hex(stableKey));
    }

    private static String sha1Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte element : hash) {
                builder.append(Character.forDigit((element >> 4) & 15, 16));
                builder.append(Character.forDigit(element & 15, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Missing SHA-1 support", exception);
        }
    }
}

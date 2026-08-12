package com.alonie.brbe.cache;

import com.alonie.brbe.mixins.accessors.ClientRecipeBookAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Recipe-viewer index backed by the <b>vanilla recipe book's known set</b>
 * ({@code ClientRecipeBook.known}).  Only recipes the player's recipe book
 * actually contains (unlocked via server packets, plus the locally injected
 * vanilla cache) are candidates — matching the "only show unlocked recipes"
 * intent.  Because entries are the real {@link RecipeDisplayEntry} objects,
 * their result icons and craftable status flow straight through to the
 * alternative-recipe overlay.
 */
public final class RecipeViewerIndex {

    private RecipeViewerIndex() {}

    /** The recipe book's known display entries, or empty if unavailable. */
    private static List<RecipeDisplayEntry> knownEntries() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return List.of();
        Map<RecipeDisplayId, RecipeDisplayEntry> known =
                ((ClientRecipeBookAccessor) mc.player.getRecipeBook()).brbe$getKnown();
        return known == null ? List.of() : List.copyOf(known.values());
    }

    /** Recipes in the book whose result is {@code target}. */
    public static List<RecipeDisplayEntry> resultsFor(ItemStack target) {
        if (target == null || target.isEmpty()) return List.of();
        List<RecipeDisplayEntry> out = new ArrayList<>();
        for (RecipeDisplayEntry entry : knownEntries()) {
            if (!isCraftingTable(entry)) continue;
            List<ItemStack> results;
            try {
                results = entry.resultItems(null);
            } catch (Exception e) {
                continue;
            }
            for (ItemStack result : results) {
                if (result.is(target.getItem())) {
                    out.add(entry);
                    break;
                }
            }
        }
        return out;
    }

    /** Recipes in the book using {@code target} as a material (tag-aware). */
    public static List<RecipeDisplayEntry> usagesFor(ItemStack target) {
        if (target == null || target.isEmpty()) return List.of();
        List<RecipeDisplayEntry> out = new ArrayList<>();
        for (RecipeDisplayEntry entry : knownEntries()) {
            if (!isCraftingTable(entry)) continue;
            Optional<List<Ingredient>> requirements = entry.craftingRequirements();
            if (requirements.isEmpty()) continue;
            for (Ingredient ingredient : requirements.get()) {
                boolean match = ingredient.items()
                        .anyMatch(holder -> holder.value() == target.getItem());
                if (match) {
                    out.add(entry);
                    break;
                }
            }
        }
        return out;
    }

    /** Furnace-type recipes whose smelted result is {@code target}.  The same
     *  smelting content is registered once per station (furnace / blast
     *  furnace / smoker / campfire), so entries with identical ingredient +
     *  result sets are merged into a single representative. */
    public static List<RecipeDisplayEntry> furnaceResultsFor(ItemStack target) {
        if (target == null || target.isEmpty()) return List.of();
        List<RecipeDisplayEntry> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (RecipeDisplayEntry entry : knownEntries()) {
            if (!isFurnaceTable(entry)) continue;
            FurnaceRecipeDisplay display = asFurnace(entry);
            if (display == null) continue;
            boolean hit = false;
            for (ItemStack result : resolveSlotDisplay(display.result())) {
                if (result.is(target.getItem())) {
                    hit = true;
                    break;
                }
            }
            if (!hit) continue;
            if (!seen.add(furnaceContentKey(display))) continue;
            out.add(entry);
        }
        return out;
    }

    /** Furnace-type recipes using {@code target} as the smelted ingredient.
     *  Entries with identical ingredient + result sets are merged (see
     *  {@link #furnaceResultsFor}). */
    public static List<RecipeDisplayEntry> furnaceUsagesFor(ItemStack target) {
        if (target == null || target.isEmpty()) return List.of();
        List<RecipeDisplayEntry> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (RecipeDisplayEntry entry : knownEntries()) {
            if (!isFurnaceTable(entry)) continue;
            FurnaceRecipeDisplay display = asFurnace(entry);
            if (display == null) continue;
            boolean hit = false;
            for (ItemStack ingredient : resolveSlotDisplay(display.ingredient())) {
                if (ingredient.is(target.getItem())) {
                    hit = true;
                    break;
                }
            }
            if (!hit) continue;
            if (!seen.add(furnaceContentKey(display))) continue;
            out.add(entry);
        }
        return out;
    }

    /** Canonical key of a furnace recipe's content: the sorted smelted
     *  ingredients and sorted results.  Identical across stations so the same
     *  recipe registered for furnace/smoker/campfire dedupes to one entry. */
    private static String furnaceContentKey(FurnaceRecipeDisplay display) {
        List<String> ingredients = new ArrayList<>();
        for (ItemStack s : resolveSlotDisplay(display.ingredient())) {
            ingredients.add(BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
        }
        ingredients.sort(String::compareTo);
        List<String> results = new ArrayList<>();
        for (ItemStack s : resolveSlotDisplay(display.result())) {
            results.add(BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
        }
        results.sort(String::compareTo);
        return String.join(",", ingredients) + "->" + String.join(",", results);
    }

    /** Cook time (ticks) of the same smelting content in each station, indexed
     *  as {furnace, blast furnace, smoker}; 0 when that station has no recipe
     *  for this content (e.g. food has no blast-furnace variant). */
    public static int[] furnaceStationTicks(RecipeDisplayEntry sample) {
        int[] ticks = new int[3];
        FurnaceRecipeDisplay sampleDisplay = asFurnace(sample);
        if (sampleDisplay == null) return ticks;
        String key = furnaceContentKey(sampleDisplay);
        for (RecipeDisplayEntry entry : knownEntries()) {
            FurnaceRecipeDisplay display = asFurnace(entry);
            if (display == null || !furnaceContentKey(display).equals(key)) continue;
            String path = categoryPath(entry);
            if (path.startsWith("furnace_")) {
                ticks[0] = display.duration();
            } else if (path.startsWith("blast_furnace_")) {
                ticks[1] = display.duration();
            } else if (path.startsWith("smoker_")) {
                ticks[2] = display.duration();
            }
        }
        return ticks;
    }

    /** Recipe-book category path of {@code entry} (e.g. "furnace_food"), or
     *  empty when unresolvable. */
    private static String categoryPath(RecipeDisplayEntry entry) {
        try {
            Identifier key = BuiltInRegistries.RECIPE_BOOK_CATEGORY.getKey(entry.category());
            return key == null ? "" : key.getPath();
        } catch (Exception e) {
            return "";
        }
    }

    /** Whether {@code entry} is a furnace recipe display. */
    public static FurnaceRecipeDisplay asFurnace(RecipeDisplayEntry entry) {
        try {
            if (entry.display() instanceof FurnaceRecipeDisplay f) return f;
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /** Resolve a {@code SlotDisplay} into concrete item stacks (best effort). */
    public static List<ItemStack> resolveSlotDisplay(SlotDisplay display) {
        List<ItemStack> out = new ArrayList<>();
        try {
            var ctx = SlotDisplayContext.fromLevel(Minecraft.getInstance().level);
            for (ItemStack stack : display.resolveForStacks(ctx)) {
                if (stack != null && !stack.isEmpty()) out.add(stack);
            }
            if (!out.isEmpty()) return out;
        } catch (Exception e) {
            // fall through to null-context
        }
        try {
            for (ItemStack stack : display.resolveForStacks(null)) {
                if (stack != null && !stack.isEmpty()) out.add(stack);
            }
        } catch (Exception e) {
            // unresolvable
        }
        return out;
    }

    /** Whether the entry belongs to a furnace-type station (furnace, blast
     *  furnace, smoker, campfire). */
    private static boolean isFurnaceTable(RecipeDisplayEntry entry) {
        try {
            Identifier key = BuiltInRegistries.RECIPE_BOOK_CATEGORY.getKey(entry.category());
            if (key == null) return false;
            String path = key.getPath();
            return path.startsWith("furnace_")
                    || path.startsWith("blast_furnace_")
                    || path.startsWith("smoker_")
                    || path.equals("campfire");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Whether the entry belongs to the crafting table (not a furnace /
     * stonecutter / smithing / other station).  Crafting-table categories all
     * have paths like {@code crafting_building_blocks}, {@code crafting_misc},
     * etc.
     */
    private static boolean isCraftingTable(RecipeDisplayEntry entry) {
        try {
            Identifier key = BuiltInRegistries.RECIPE_BOOK_CATEGORY.getKey(entry.category());
            return key != null && key.getPath().startsWith("crafting_");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Wrap the query hits into a {@link RecipeCollection} for the vanilla
     * alternative-recipe overlay.  Every entry is selected; craftability is
     * computed against the player's {@code stackedContents} so the overlay
     * shows craftable vs not.
     */
    public static RecipeCollection toCollection(List<RecipeDisplayEntry> entries,
                                                StackedItemContents stackedContents) {
        RecipeCollection collection = new RecipeCollection(entries);
        collection.selectRecipes(stackedContents, display -> true);
        viewerCollections.add(collection);
        return collection;
    }

    /** Viewer collections created by {@link #toCollection}. */
    private static final java.util.Set<RecipeCollection> viewerCollections =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    /** Whether {@code collection} is one created for the BRBE R/U viewer. */
    public static boolean isViewerCollection(RecipeCollection collection) {
        return collection != null && viewerCollections.contains(collection);
    }

    /**
     * Strong snapshot of each viewer collection's partially-craftable recipe
     * IDs, captured at open time.  Independent of {@code PartialCraftingUtil}'s
     * generation-aware tagger (whose generation advances on every recipe-book
     * updateCollections and can silently invalidate the viewer's marks, making
     * partial recipes flip back to "uncraftable" mid-overlay).
     */
    private static final java.util.Map<RecipeCollection, java.util.Set<RecipeDisplayId>> viewerPartials =
            new java.util.IdentityHashMap<>();

    /** Snapshot the viewer collection's partial IDs (call after prepareForViewer). */
    public static void snapshotPartials(RecipeCollection collection) {
        if (collection == null) return;
        java.util.Set<RecipeDisplayId> ids = new java.util.HashSet<>();
        for (RecipeDisplayEntry entry : collection.getRecipes()) {
            if (com.alonie.brbe.util.PartialCraftingUtil.isPartiallyCraftableEvenIfStale(collection, entry.id())) {
                ids.add(entry.id());
            }
        }
        viewerPartials.put(collection, ids);
    }

    /** Whether {@code id} is a snapshot partial of a viewer collection. */
    public static boolean isViewerPartial(RecipeCollection collection, RecipeDisplayId id) {
        if (collection == null || id == null) return false;
        java.util.Set<RecipeDisplayId> ids = viewerPartials.get(collection);
        return ids != null && ids.contains(id);
    }

    /** Drop the snapshot for a collection when it is no longer on screen. */
    public static void clearViewerPartials(RecipeCollection collection) {
        if (collection != null) viewerPartials.remove(collection);
    }

    /** Whether a BRBE R/U viewer overlay is currently open. */
    private static volatile boolean viewerActive;

    /** Whether the current viewer was opened from a recipe-book button (R/U on
     *  a recipe-book recipe) rather than a container slot / ghost item.  Only
     *  book-opened viewers close on page change. */
    private static volatile boolean viewerOpenedFromBook;

    public static void setViewerActive(boolean active) {
        viewerActive = active;
        if (!active) viewerOpenedFromBook = false;
    }

    public static boolean isViewerActive() {
        return viewerActive;
    }

    public static void setViewerOpenedFromBook(boolean fromBook) {
        viewerOpenedFromBook = fromBook;
    }

    public static boolean isViewerOpenedFromBook() {
        return viewerOpenedFromBook;
    }
}

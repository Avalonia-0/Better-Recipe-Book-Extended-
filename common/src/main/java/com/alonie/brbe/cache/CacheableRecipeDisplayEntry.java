package com.alonie.brbe.cache;

import com.alonie.brbe.BetterRecipeBook;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.display.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Serializable snapshot of a {@link RecipeDisplayEntry} for the local recipe cache.
 *
 * Uses Minecraft's {@link ContextMap} (from {@link SlotDisplayContext#fromLevel})
 * to resolve SlotDisplay → item IDs during capture. Reconstructs
 * RecipeDisplayEntry objects during injection.
 */
public final class CacheableRecipeDisplayEntry {

    @SerializedName("key")
    private String recipeKey;

    @SerializedName("type")
    private String type;

    @SerializedName("width")
    private Integer width;

    @SerializedName("height")
    private Integer height;

    @SerializedName("ingredients")
    private List<List<String>> ingredients;

    @SerializedName("resultItem")
    private String resultItem;

    @SerializedName("resultCount")
    private Integer resultCount;

    @SerializedName("craftingStation")
    private String craftingStation;

    @SerializedName("category")
    private String categoryName;

    @SerializedName("group")
    private Integer group;

    public CacheableRecipeDisplayEntry() {}

    public String recipeKey() { return recipeKey; }

    /** The result item registry ID (e.g. "minecraft:bread"), or null if not available. */
    public String resultItem() { return resultItem; }

    /** The recipe book category (e.g. "crafting_building_blocks"), or null. */
    public String categoryName() { return categoryName; }

    // ======== From JSON ========

    /**
     * Parse a vanilla recipe JSON into a cacheable entry.
     * Uses {@link VanillaRecipeLoader}'s helper methods for field extraction.
     *
     * @param json raw recipe JSON from data/minecraft/recipe/
     * @param recipeId the recipe identifier (e.g. "bread")
     * @return populated entry, or null if unsupported
     */
    public static CacheableRecipeDisplayEntry fromJson(JsonObject json, String recipeId) {
        // Skip special/hidden recipe types
        if (VanillaRecipeLoader.shouldSkip(json)) return null;

        String type = VanillaRecipeLoader.mapType(json);
        if (type == null) return null;

        CacheableRecipeDisplayEntry c = new CacheableRecipeDisplayEntry();
        c.type = type;

        switch (type) {
            case "shaped":
                c.width = VanillaRecipeLoader.extractShapedWidth(json);
                c.height = VanillaRecipeLoader.extractShapedHeight(json);
                c.ingredients = VanillaRecipeLoader.extractShapedIngredients(json);
                break;
            case "shapeless":
                c.ingredients = VanillaRecipeLoader.extractShapelessIngredients(json);
                break;
            case "furnace":
            case "stonecutter":
                c.ingredients = VanillaRecipeLoader.extractSingleIngredient(json);
                break;
            case "smithing_transform":
            case "smithing_trim":
                // Smithing recipes: ingredients not needed for display, just result
                c.ingredients = null;
                break;
        }

        c.resultItem = VanillaRecipeLoader.extractResultItem(json);
        c.resultCount = VanillaRecipeLoader.extractResultCount(json);
        c.craftingStation = VanillaRecipeLoader.extractCraftingStation(type);
        c.categoryName = VanillaRecipeLoader.mapCategory(json, type);
        c.group = VanillaRecipeLoader.mapGroup(json);

        // Stable key: type + recipe ID
        c.recipeKey = type + "/" + recipeId;

        return c;
    }

    // ======== Inject ========

    @SuppressWarnings("deprecation")
    public RecipeDisplayEntry toEntry(RecipeDisplayId newId) {
        SlotDisplay result = makeSlotDisplay(resultItem, resultCount != null ? resultCount : 1);
        SlotDisplay station = craftingStation != null
                ? makeSlotDisplay(craftingStation, 1)
                : SlotDisplay.Empty.INSTANCE;

        RecipeDisplay display;
        switch (type != null ? type : "unknown") {
            case "shaped":
                if (width == null || height == null || ingredients == null) return null;
                List<SlotDisplay> shaped = new ArrayList<>();
                for (List<String> alts : ingredients) {
                    shaped.add(makeSlotFromAlternatives(alts));
                }
                display = new ShapedCraftingRecipeDisplay(width, height, shaped, result, station);
                break;

            case "shapeless":
                if (ingredients == null) return null;
                List<SlotDisplay> shapeless = new ArrayList<>();
                for (List<String> alts : ingredients) {
                    shapeless.add(makeSlotFromAlternatives(alts));
                }
                display = new ShapelessCraftingRecipeDisplay(shapeless, result, station);
                break;

            case "furnace":
                SlotDisplay furnaceIngredient = (ingredients != null && !ingredients.isEmpty()
                        && ingredients.get(0) != null && !ingredients.get(0).isEmpty())
                        ? makeSlotDisplay(ingredients.get(0).get(0), 1)
                        : SlotDisplay.Empty.INSTANCE;
                display = new FurnaceRecipeDisplay(furnaceIngredient,
                        SlotDisplay.Empty.INSTANCE, result,
                        SlotDisplay.Empty.INSTANCE, 100, 0f);
                break;

            default:
                // Fallback: minimal shapeless display for unknown/smithing/stonecutter
                List<SlotDisplay> fallback = new ArrayList<>();
                if (ingredients != null) {
                    for (List<String> alts : ingredients) {
                        fallback.add(makeSlotFromAlternatives(alts));
                    }
                }
                display = new ShapelessCraftingRecipeDisplay(fallback, result, station);
                break;
        }

        RecipeBookCategory category = categoryByName(categoryName);
        OptionalInt optGroup = group != null && group >= 0
                ? OptionalInt.of(group) : OptionalInt.empty();

        return new RecipeDisplayEntry(newId, display, optGroup, category, Optional.empty());
    }

    // ======== Reconstruction helpers ========

    private static RecipeBookCategory categoryByName(String name) {
        if (name == null) return RecipeBookCategories.CRAFTING_MISC;
        String[] parts = name.split(":", 2);
        Identifier id = parts.length == 2
                ? Identifier.fromNamespaceAndPath(parts[0], parts[1])
                : Identifier.fromNamespaceAndPath("minecraft", parts[0]);
        return BuiltInRegistries.RECIPE_BOOK_CATEGORY.getOptional(id)
                .orElse(RecipeBookCategories.CRAFTING_MISC);
    }

    private static SlotDisplay makeSlotDisplay(String itemId, int count) {
        if (itemId == null) return SlotDisplay.Empty.INSTANCE;

        if (itemId.startsWith("#")) {
            String tagStr = itemId.substring(1);
            String[] parts = tagStr.split(":", 2);
            Identifier id = parts.length == 2
                    ? Identifier.fromNamespaceAndPath(parts[0], parts[1])
                    : Identifier.fromNamespaceAndPath("minecraft", parts[0]);
            TagKey<Item> tagKey = TagKey.create(BuiltInRegistries.ITEM.key(), id);
            return new SlotDisplay.TagSlotDisplay(tagKey);
        }

        String[] parts = itemId.split(":", 2);
        Identifier id = parts.length == 2
                ? Identifier.fromNamespaceAndPath(parts[0], parts[1])
                : Identifier.fromNamespaceAndPath("minecraft", parts[0]);
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) return SlotDisplay.Empty.INSTANCE;

        if (count <= 1) {
            return new SlotDisplay.ItemSlotDisplay(item);
        } else {
            ItemStackTemplate template = new ItemStackTemplate(item, count);
            return new SlotDisplay.ItemStackSlotDisplay(template);
        }
    }

    private static SlotDisplay makeSlotFromAlternatives(List<String> altItemIds) {
        if (altItemIds == null || altItemIds.isEmpty()) {
            return SlotDisplay.Empty.INSTANCE;
        }
        if (altItemIds.size() == 1) {
            return makeSlotDisplay(altItemIds.get(0), 1);
        }
        List<SlotDisplay> children = new ArrayList<>();
        for (String id : altItemIds) {
            var child = makeSlotDisplay(id, 1);
            if (!(child instanceof SlotDisplay.Empty)) {
                children.add(child);
            }
        }
        return children.isEmpty()
                ? SlotDisplay.Empty.INSTANCE
                : new SlotDisplay.Composite(children);
    }
}

package com.alonie.brbe.cache;

import com.alonie.brbe.BetterRecipeBook;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.display.*;
import net.minecraft.world.item.equipment.trim.TrimPattern;

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

    @SerializedName("template")
    private List<List<String>> templateIngredients;

    @SerializedName("base")
    private List<List<String>> baseIngredients;

    @SerializedName("addition")
    private List<List<String>> additionIngredients;

    /** Trim pattern id (e.g. {@code minecraft:coast}); smithing_trim recipes
     *  have no {@code result} field, their product is pattern-derived. */
    @SerializedName("pattern")
    private String trimPattern;

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

    /** Trim pattern id of a smithing_trim recipe, or null for other types. */
    public String trimPattern() { return trimPattern; }

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
            case "transmute":
                c.ingredients = VanillaRecipeLoader.extractTransmuteIngredients(json);
                break;
            case "furnace":
            case "stonecutter":
                c.ingredients = VanillaRecipeLoader.extractSingleIngredient(json);
                break;
            case "smithing_transform":
            case "smithing_trim":
                // Smithing recipes: template / base / addition are carried
                // separately so the cache entry reconstructs a real
                // SmithingRecipeDisplay (the empty-slot shapeless fallback
                // rendered as a blank button in the query viewer).
                c.ingredients = null;
                c.templateIngredients = VanillaRecipeLoader.extractSmithingSlot(json, "template");
                c.baseIngredients = VanillaRecipeLoader.extractSmithingSlot(json, "base");
                c.additionIngredients = VanillaRecipeLoader.extractSmithingSlot(json, "addition");
                if ("smithing_trim".equals(type)) {
                    JsonElement pattern = json.get("pattern");
                    if (pattern != null && !pattern.isJsonNull()) {
                        c.trimPattern = pattern.getAsString();
                    }
                }
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

            case "stonecutter":
                SlotDisplay stonecutterInput = (ingredients != null && !ingredients.isEmpty()
                        && ingredients.get(0) != null && !ingredients.get(0).isEmpty())
                        ? makeSlotDisplay(ingredients.get(0).get(0), 1)
                        : SlotDisplay.Empty.INSTANCE;
                display = new StonecutterRecipeDisplay(stonecutterInput, result, station);
                break;

            case "smithing_transform":
                // A real SmithingRecipeDisplay: the query viewer renders
                // template / base / addition / result and matches the entry
                // back to its JEI layout; the shapeless fallback below renders
                // as a blank placeholder.
                display = new SmithingRecipeDisplay(
                        makeSlotFromAlternatives(firstAlternatives(templateIngredients)),
                        makeSlotFromAlternatives(firstAlternatives(baseIngredients)),
                        makeSlotFromAlternatives(firstAlternatives(additionIngredients)),
                        result, station);
                break;

            case "smithing_trim":
                // Trim recipes: the result is the pattern-derived demo display
                // (same structure as SmithingTrimRecipe.display()), not an item
                // slot — the recipe JSON has no result field.
                SlotDisplay trimBase = makeSlotFromAlternatives(firstAlternatives(baseIngredients));
                SlotDisplay trimAddition = makeSlotFromAlternatives(firstAlternatives(additionIngredients));
                Holder<TrimPattern> pattern = trimPattern == null ? null : trimPatternHolder(trimPattern);
                if (pattern == null) return null;
                display = new SmithingRecipeDisplay(
                        makeSlotFromAlternatives(firstAlternatives(templateIngredients)),
                        trimBase, trimAddition,
                        new SlotDisplay.SmithingTrimDemoSlotDisplay(trimBase, trimAddition, pattern),
                        station);
                break;

            case "transmute":
                // Transmute recipes (shulker box dyeing, bundle dyeing) have two
                // ingredients: input (the thing being transformed) and material
                // (the dye/catalyst).  Rendered as shapeless slots.
                if (ingredients == null || ingredients.isEmpty()) return null;
                List<SlotDisplay> transmuteSlots = new ArrayList<>();
                for (List<String> alts : ingredients) {
                    transmuteSlots.add(makeSlotFromAlternatives(alts));
                }
                display = new ShapelessCraftingRecipeDisplay(transmuteSlots, result, station);
                break;

            default:
                // Fallback: minimal shapeless display for unknown recipe types
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

        // Handle tag references: "#minecraft:logs"
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
            // 数量 >1 的结果（如曲奇×8）在查看浮层/配方书中显示数量角标。
            // 1.21.11 的 ItemStackSlotDisplay 直接接收 ItemStack（26.2 用 ItemStackTemplate）。
            return new SlotDisplay.ItemStackSlotDisplay(new ItemStack(item, count));
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

    /** The alternatives list of one smithing slot field, or null when absent. */
    private static List<String> firstAlternatives(List<List<String>> slot) {
        return slot == null || slot.isEmpty() ? null : slot.get(0);
    }

    /** The trim-pattern holder for {@code patternId} from the current level's
     *  registry, or null when unavailable (the entry is then filtered). */
    private static Holder<TrimPattern> trimPatternHolder(String patternId) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return null;
            Registry<TrimPattern> registry =
                    mc.level.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN);
            return registry.get(Identifier.parse(patternId)).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}

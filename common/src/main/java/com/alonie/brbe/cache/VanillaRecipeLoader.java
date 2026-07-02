package com.alonie.brbe.cache;

import com.alonie.brbe.BetterRecipeBook;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Loads vanilla Minecraft recipe JSONs from the classpath at startup.
 *
 * Recipes are read from {@code data/minecraft/recipe/*.json} inside the
 * Minecraft merged JAR (or file tree in dev). Each JSON is parsed into
 * a {@link CacheableRecipeDisplayEntry} for later injection.
 *
 * Inspired by ReliableRecipeViewer's local recipe fallback.
 */
public final class VanillaRecipeLoader {
    private static final String RECIPE_DIR = "data/minecraft/recipe/";
    private static final String JSON_EXT = ".json";

    // Special recipe types that have no GUI representation (isSpecial() == true)
    // crafting_transmute is NOT special — it is a first-class recipe type used for
    // dyeing shulker boxes and bundles.  It has full JSON representation and a
    // standard SlotDisplay; we handle it like shapeless crafting.
    private static final Set<String> SKIP_TYPES = Set.of(
            "minecraft:crafting_decorated_pot",
            "minecraft:crafting_special_bookcloning",
            "minecraft:crafting_special_mapextending",
            "minecraft:crafting_special_firework_rocket",
            "minecraft:crafting_special_firework_star",
            "minecraft:crafting_special_firework_star_fade",
            "minecraft:crafting_special_bannerduplicate",
            "minecraft:crafting_special_shielddecoration",
            "minecraft:crafting_special_repairitem",
            "minecraft:crafting_dye",
            "minecraft:crafting_imbue"
    );

    // JSON "category" → RecipeBookCategory registry name (prefix is applied per type)
    private static final Map<String, String> CRAFTING_CATEGORY_MAP = Map.of(
            "building", "crafting_building_blocks",
            "redstone", "crafting_redstone",
            "equipment", "crafting_equipment",
            "misc", "crafting_misc"
    );

    private static final Map<String, String> COOKING_CATEGORY_MAP = Map.of(
            "food", "furnace_food",
            "blocks", "furnace_blocks",
            "misc", "furnace_misc"
    );

    private VanillaRecipeLoader() {}

    /**
     * Load all vanilla recipes from the classpath.
     * @return list of cacheable entries ready for injection
     */
    public static List<CacheableRecipeDisplayEntry> loadAll() {
        List<CacheableRecipeDisplayEntry> result = new ArrayList<>();
        int parsed = 0;

        try {
            // Use Minecraft class location to find the vanilla JAR (not NeoForge patched)
            URL mcUrl = net.minecraft.world.level.block.Blocks.class
                    .getProtectionDomain().getCodeSource().getLocation();
            if (mcUrl == null) {
                BetterRecipeBook.LOGGER.warn("[BRBE-CACHE] cannot locate Minecraft JAR");
                return result;
            }

            File jarFile = new File(mcUrl.toURI());
            BetterRecipeBook.LOGGER.info("[BRBE-CACHE] scanning JAR: {}", jarFile);
            parsed = scanJar(jarFile, result);
        } catch (Exception e) {
            BetterRecipeBook.LOGGER.warn("[BRBE-CACHE] error scanning recipes: {}", e.getMessage());
        }

        BetterRecipeBook.LOGGER.info(
                "[BRBE-CACHE] loaded {} recipe entries from classpath", result.size());
        return result;
    }

    /** Scan a JAR file for data/minecraft/recipe/*.json entries. */
    private static int scanJar(File jarFile, List<CacheableRecipeDisplayEntry> out) {
        int count = 0;
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith(RECIPE_DIR) && name.endsWith(JSON_EXT)) {
                    String recipeId = name.substring(RECIPE_DIR.length(), name.length() - JSON_EXT.length());
                    try (InputStream is = jar.getInputStream(entry);
                         Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        CacheableRecipeDisplayEntry cEntry = CacheableRecipeDisplayEntry.fromJson(json, recipeId);
                        if (cEntry != null) {
                            out.add(cEntry);
                            count++;
                        }
                    }
                }
            }
        } catch (IOException e) {
            BetterRecipeBook.LOGGER.warn("[BRBE-CACHE] error reading JAR: {}", e.getMessage());
        }
        return count;
    }

    /** Scan a directory for .json recipe files (dev environment). */
    private static int scanDirectory(File dir, List<CacheableRecipeDisplayEntry> out) {
        int count = 0;
        File[] files = dir.listFiles((d, name) -> name.endsWith(JSON_EXT));
        if (files == null) return 0;

        for (File file : files) {
            String recipeId = file.getName();
            if (recipeId.endsWith(JSON_EXT)) {
                recipeId = recipeId.substring(0, recipeId.length() - JSON_EXT.length());
            }
            try (Reader reader = new FileReader(file, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                CacheableRecipeDisplayEntry cEntry = CacheableRecipeDisplayEntry.fromJson(json, recipeId);
                if (cEntry != null) {
                    out.add(cEntry);
                    count++;
                }
            } catch (IOException e) {
                BetterRecipeBook.LOGGER.warn("[BRBE-CACHE] error reading {}: {}", file, e.getMessage());
            }
        }
        return count;
    }

    // ======== JSON parsing helpers (used from CacheableRecipeDisplayEntry) ========

    /** Determine our internal "type" from the JSON "type" field. */
    static String mapType(JsonObject json) {
        String t = json.get("type").getAsString();
        if (t.equals("minecraft:crafting_shaped")) return "shaped";
        if (t.equals("minecraft:crafting_shapeless")) return "shapeless";
        if (t.equals("minecraft:smelting") || t.equals("minecraft:blasting")
                || t.equals("minecraft:smoking") || t.equals("minecraft:campfire_cooking")) return "furnace";
        if (t.equals("minecraft:stonecutting")) return "stonecutter";
        if (t.equals("minecraft:smithing_transform")) return "smithing_transform";
        if (t.equals("minecraft:smithing_trim")) return "smithing_trim";
        if (t.equals("minecraft:crafting_transmute")) return "transmute";
        return null; // skip
    }

    /** Map JSON "category" + "type" to RecipeBookCategory registry name. */
    static String mapCategory(JsonObject json, String type) {
        // Smithing and stonecutter have fixed categories
        if ("smithing_transform".equals(type) || "smithing_trim".equals(type)) return "smithing";
        if ("stonecutter".equals(type)) return "stonecutter";

        String rawCategory = json.has("category") ? json.get("category").getAsString() : "misc";
        String jsonType = json.get("type").getAsString();

        if (jsonType.equals("minecraft:smoking")) return "smoker_food";
        if (jsonType.equals("minecraft:campfire_cooking")) return "campfire";
        if (jsonType.equals("minecraft:blasting")) {
            return "blocks".equals(rawCategory) ? "blast_furnace_blocks" : "blast_furnace_misc";
        }

        if (jsonType.equals("minecraft:smelting")) {
            return COOKING_CATEGORY_MAP.getOrDefault(rawCategory, "furnace_misc");
        }

        // Crafting
        return CRAFTING_CATEGORY_MAP.getOrDefault(rawCategory, "crafting_misc");
    }

    /** Extract the "group" field (optional string). */
    static int mapGroup(JsonObject json) {
        if (json.has("group")) {
            String g = json.get("group").getAsString();
            if (g != null && !g.isEmpty()) {
                // Hash the group string to a stable int
                return g.hashCode() & 0x7FFFFFFF;
            }
        }
        return -1;
    }

    /** Check if this recipe type should be skipped (special/unsupported). */
    static boolean shouldSkip(JsonObject json) {
        return SKIP_TYPES.contains(json.get("type").getAsString());
    }

    // ======== Ingredient extraction from JSON ========

    /** Extract result item from JSON "result" object. */
    static String extractResultItem(JsonObject json) {
        JsonObject result = json.getAsJsonObject("result");
        if (result == null || !result.has("id")) return null;
        return result.get("id").getAsString();
    }

    /** Extract result count from JSON "result" object. */
    static int extractResultCount(JsonObject json) {
        JsonObject result = json.getAsJsonObject("result");
        if (result == null) return 1;
        return result.has("count") ? result.get("count").getAsInt() : 1;
    }

    /** Extract crafting station item ID based on recipe type. */
    static String extractCraftingStation(String type) {
        switch (type) {
            case "furnace": return "minecraft:furnace";
            case "stonecutter": return "minecraft:stonecutter";
            case "smithing_transform":
            case "smithing_trim": return "minecraft:smithing_table";
            default: return "minecraft:crafting_table";
        }
    }

    /**
     * Extract ingredient list from a shaped recipe's "key" + "pattern" fields.
     * Returns a flattened list of ingredient alternatives, one per grid cell.
     * Empty cells are represented as null in the list.
     */
    static List<List<String>> extractShapedIngredients(JsonObject json) {
        JsonObject key = json.getAsJsonObject("key");
        JsonArray pattern = json.getAsJsonArray("pattern");
        if (key == null || pattern == null) return Collections.emptyList();

        int width = 0;
        for (JsonElement row : pattern) {
            String rowStr = row.getAsString();
            if (rowStr.length() > width) width = rowStr.length();
        }
        int height = pattern.size();

        // Pre-parse key entries: char → list of item IDs
        Map<Character, List<String>> keyMap = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : key.entrySet()) {
            char c = e.getKey().charAt(0);
            keyMap.put(c, parseIngredientEntry(e.getValue()));
        }

        // Build flattened grid
        List<List<String>> ingredients = new ArrayList<>(width * height);
        for (int y = 0; y < height; y++) {
            String rowStr = pattern.get(y).getAsString();
            for (int x = 0; x < width; x++) {
                if (x < rowStr.length() && rowStr.charAt(x) != ' ') {
                    char c = rowStr.charAt(x);
                    ingredients.add(keyMap.getOrDefault(c, null));
                } else {
                    ingredients.add(null); // empty slot
                }
            }
        }
        return ingredients;
    }

    /**
     * Extract ingredient list from a shapeless recipe's "ingredients" array.
     * Each element is an Ingredient (item, tag, or list).
     */
    static List<List<String>> extractShapelessIngredients(JsonObject json) {
        JsonArray arr = json.getAsJsonArray("ingredients");
        if (arr == null) return Collections.emptyList();

        List<List<String>> ingredients = new ArrayList<>();
        for (JsonElement elem : arr) {
            ingredients.add(parseIngredientEntry(elem));
        }
        return ingredients;
    }

    /** Parse a single ingredient entry (item ID, tag, or list of item IDs). */
    private static List<String> parseIngredientEntry(JsonElement elem) {
        if (elem.isJsonPrimitive()) {
            // Single item: "minecraft:wheat" or tag: "#minecraft:logs"
            String s = elem.getAsString();
            return s.startsWith("#") ? List.of(s) : List.of(s);
        } else if (elem.isJsonArray()) {
            // List of alternatives: ["minecraft:oak_planks", "minecraft:spruce_planks"]
            List<String> items = new ArrayList<>();
            for (JsonElement child : elem.getAsJsonArray()) {
                if (child.isJsonPrimitive()) {
                    String s = child.getAsString();
                    if (!s.startsWith("#")) {
                        items.add(s);
                    }
                }
            }
            return items.isEmpty() ? null : items;
        } else if (elem.isJsonObject()) {
            // Inline item: {"item": "minecraft:wheat"} or {"tag": "minecraft:logs"}
            JsonObject obj = elem.getAsJsonObject();
            if (obj.has("item")) return List.of(obj.get("item").getAsString());
            if (obj.has("tag")) return List.of("#" + obj.get("tag").getAsString());
        }
        return null; // empty/unrecognized
    }

    /** Extract width from shaped recipe's pattern. */
    static int extractShapedWidth(JsonObject json) {
        JsonArray pattern = json.getAsJsonArray("pattern");
        if (pattern == null) return 0;
        int width = 0;
        for (JsonElement row : pattern) {
            int len = row.getAsString().length();
            if (len > width) width = len;
        }
        return width;
    }

    /** Extract height from shaped recipe's pattern. */
    static int extractShapedHeight(JsonObject json) {
        JsonArray pattern = json.getAsJsonArray("pattern");
        return pattern != null ? pattern.size() : 0;
    }

    /**
     * Extract ingredients from a transmute recipe's "input" + "material" fields.
     * Transmute recipes (shulker box dyeing, bundle dyeing) have an input item
     * (the thing being transformed) and a material (the dye/catalyst consumed).
     * Both are rendered as ingredient slots in the recipe display.
     */
    static List<List<String>> extractTransmuteIngredients(JsonObject json) {
        List<List<String>> ingredients = new ArrayList<>();
        JsonElement input = json.get("input");
        if (input != null) {
            List<String> parsed = parseIngredientEntry(input);
            if (parsed != null) ingredients.add(parsed);
        }
        JsonElement material = json.get("material");
        if (material != null) {
            List<String> parsed = parseIngredientEntry(material);
            if (parsed != null) ingredients.add(parsed);
        }
        return ingredients.isEmpty() ? null : ingredients;
    }

    /** Extract a single ingredient from cooking/stonecutting recipes ("ingredient" field). */
    static List<List<String>> extractSingleIngredient(JsonObject json) {
        JsonElement ingredient = json.get("ingredient");
        if (ingredient == null) return null;
        List<String> parsed = parseIngredientEntry(ingredient);
        return parsed != null ? List.of(parsed) : null;
    }
}

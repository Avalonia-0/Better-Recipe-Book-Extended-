package com.alonie.brbe.jei.plugins.engine;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.recipeviewer.RecipeViewerCategories;
import com.alonie.brbe.recipeviewer.RecipeViewerCategory;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns the plugins' collected categories, recipes and workstation catalysts
 * into BRBE query-engine types: for each recipe type it runs
 * {@code setRecipe} through the data-only builder, synthesizes a
 * {@link RecipeDisplayEntry} per recipe, and registers the type plus a dynamic
 * viewer category.  Recipe types that share a workstation block (e.g. bclib's
 * leveled anvils — one type per level, all handled by the same anvils) are
 * merged into one category so the tab strip shows one "Anvil" instead of one
 * per level.
 */
public final class PluginRecipeIndexer {

    private PluginRecipeIndexer() {}

    /** A recipe type that produced indexable recipes, ready for category display. */
    private record TypeInfo(String uid, Component title, List<ItemStack> stations) {
        Set<Identifier> stationIds() {
            Set<Identifier> ids = new LinkedHashSet<>();
            for (ItemStack stack : stations) {
                if (stack != null && !stack.isEmpty()) {
                    Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    if (id != null) ids.add(id);
                }
            }
            return ids;
        }
    }

    /** A mod recipe's category and raw recipe object, kept so the renderer can
     *  call {@code category.draw(recipe, ...)} for the full JEI UI. */
    public record RenderEntry(IRecipeCategory<?> category, Object recipe) {}

    /** synthetic id → category + raw recipe, filled during {@link #indexAll}. */
    private static final Map<RecipeDisplayId, RenderEntry> RENDER_ENTRIES = new HashMap<>();

    /** The render entry for a synthetic recipe id, or null. */
    public static RenderEntry renderEntryFor(RecipeDisplayId id) {
        return id == null ? null : RENDER_ENTRIES.get(id);
    }

    public static void indexAll(List<IRecipeCategory<?>> categories,
                                Map<IRecipeType<?>, List<Object>> recipes,
                                Map<Identifier, Set<Identifier>> catalysts,
                                Map<IRecipeCategory<?>, RecipeViewerEngine.RecipeBackground> backgrounds) {
        RENDER_ENTRIES.clear();
        // The cached JEI drawable layouts hold the old categories/recipes.
        SyntheticRecipeRendererImpl.invalidate();
        Map<IRecipeType<?>, IRecipeCategory<?>> categoryByType = new HashMap<>();
        Map<String, IRecipeCategory<?>> categoryByUid = new HashMap<>();
        for (IRecipeCategory<?> category : categories) {
            categoryByType.put(category.getRecipeType(), category);
            categoryByUid.put(category.getRecipeType().getUid().toString(), category);
        }

        List<TypeInfo> typeInfos = new ArrayList<>();
        int typeCount = 0;
        int recipeCount = 0;

        for (Map.Entry<IRecipeType<?>, List<Object>> entry : recipes.entrySet()) {
            IRecipeType<?> recipeType = entry.getKey();
            IRecipeCategory<?> category = categoryByType.get(recipeType);
            if (category == null) {
                BetterRecipeBook.LOGGER.info("[BRBE-JEI-Plugins] no category for recipe type {}; skipping",
                        recipeType.getUid());
                continue;
            }
            String uid = recipeType.getUid().toString();
            List<ItemStack> stations = stationsFor(recipeType.getUid(), catalysts);
            RecipeViewerEngine.RecipeBackground background = backgrounds.get(category);
            BetterRecipeBook.LOGGER.info("[BRBE-JEI-Plugins] type {} background={}",
                    uid, background == null ? "none" : background.texture());

            List<RecipeViewerEngine.IndexedRecipe> indexed = new ArrayList<>();
            // Distinct (inputs, products) pairs within this type: modpacks can
            // ship duplicate recipes, which would otherwise appear as identical
            // split entries.
            Set<String> seenRecipes = new HashSet<>();
            for (Object recipe : entry.getValue()) {
                try {
                    DataOnlyLayoutBuilder builder = new DataOnlyLayoutBuilder(category.getWidth(), category.getHeight());
                    runSetRecipe(category, recipe, builder);
                    List<SlotData> slots = builder.slotData();
                    List<ItemStack> inputs = new ArrayList<>();
                    Map<Item, ItemStack> productsByItem = new LinkedHashMap<>();
                    for (SlotData slot : slots) {
                        if (slot.role() == RecipeIngredientRole.OUTPUT && slot.visible()) {
                            // Player-obtainable products: every stack in a visible
                            // OUTPUT slot, de-duplicated by item so variant stacks
                            // or repeated slots collapse into one product.
                            // Invisible slots are data-only (not rendered, not a
                            // product) and are excluded, matching toLayout.
                            for (ItemStack stack : slot.stacks()) {
                                if (stack != null && !stack.isEmpty()) {
                                    productsByItem.putIfAbsent(stack.getItem(), stack);
                                }
                            }
                        } else if (slot.role() == RecipeIngredientRole.INPUT
                                || slot.role() == RecipeIngredientRole.CRAFTING_STATION) {
                            inputs.addAll(slot.stacks());
                        }
                    }
                    List<ItemStack> products = new ArrayList<>(productsByItem.values());
                    if (inputs.isEmpty() && products.isEmpty()) {
                        continue;
                    }
                    if (!seenRecipes.add(fingerprint(inputs, products))) {
                        BetterRecipeBook.LOGGER.info("[BRBE-JEI-Plugins] duplicate {} recipe skipped",
                                recipeType.getUid());
                        continue;
                    }
                    // Multi-product recipes stay as ONE entry whose result
                    // carries every product (a composite display): the viewer
                    // shows a single object that cycles through the products
                    // (like the smithing category) instead of one split button
                    // per product.  The engine indexes the entry under every
                    // product, so result lookup still gates on each product,
                    // and the groupKey keeps usage lookups to one entry per
                    // recipe.
                    RecipeViewerEngine.RecipeLayout layout = toLayout(builder, slots, background);
                    Object groupKey = recipe;
                    if (products.isEmpty()) {
                        RecipeDisplayEntry synthetic =
                                SyntheticRecipeDisplayEntryFactory.createForOutput(slots, stations, List.of());
                        RENDER_ENTRIES.put(synthetic.id(), new RenderEntry(category, recipe));
                        RecipeViewerEngine.registerLayout(synthetic.id(), layout);
                        indexed.add(new RecipeViewerEngine.IndexedRecipe(synthetic, inputs, List.of(), groupKey));
                    } else {
                        RecipeDisplayEntry synthetic =
                                SyntheticRecipeDisplayEntryFactory.createForOutput(slots, stations, products);
                        RENDER_ENTRIES.put(synthetic.id(), new RenderEntry(category, recipe));
                        RecipeViewerEngine.registerLayout(synthetic.id(), layout);
                        indexed.add(new RecipeViewerEngine.IndexedRecipe(synthetic, inputs, products, groupKey));
                    }
                } catch (Exception | LinkageError ex) {
                    BetterRecipeBook.LOGGER.warn("[BRBE-JEI-Plugins] failed to index a {} recipe: {}",
                            recipeType.getUid(), ex.toString());
                }
            }

            if (indexed.isEmpty()) {
                BetterRecipeBook.LOGGER.info("[BRBE-JEI-Plugins] recipe type {} yielded no indexable recipes; skipping",
                        recipeType.getUid());
                continue;
            }
            RecipeViewerEngine.registerType(uid, indexed, stations);
            int groupCount = (int) indexed.stream().map(RecipeViewerEngine.IndexedRecipe::groupKey).distinct().count();
            if (groupCount != indexed.size()) {
                BetterRecipeBook.LOGGER.info("[BRBE-JEI-Plugins] type {}: {} split entries collapse to {} recipe groups (usage dedup)",
                        uid, indexed.size(), groupCount);
            }
            typeInfos.add(new TypeInfo(uid, category.getTitle(), stations));
            typeCount++;
            recipeCount += indexed.size();
        }

        // Recipe-book-driven types: a mod recipe book's unlocked known entries
        // are the authoritative data source for its JEI recipe type.  Each
        // known entry's display declares its crafting station (the cooking pot
        // for Farmer's Delight cooking recipes); matching that station against
        // the collected catalysts ties the recipe-book category to the JEI
        // type with no per-mod hard-coding, and progression unlocks (or the
        // mod's auto-unlock config) flow into the viewer category
        // automatically.  Registered after the JEI full collection so the
        // recipe-book data wins when both sources exist.
        Map<String, List<RecipeViewerEngine.IndexedRecipe>> bookDriven = new LinkedHashMap<>();
        for (RecipeDisplayEntry knownEntry : RecipeViewerIndex.knownEntries()) {
            try {
                // Only mod recipe-book categories participate: vanilla
                // entries are managed by rebuildEngine, and their displays
                // must not be re-attributed through a mod's catalysts.
                Identifier catKey = BuiltInRegistries.RECIPE_BOOK_CATEGORY.getKey(knownEntry.category());
                if (catKey == null || "minecraft".equals(catKey.getNamespace())) continue;
                ItemStack station = RecipeViewerIndex.resolveCraftingStation(knownEntry);
                if (station.isEmpty()) continue;
                String uid = typeUidForStation(station, catalysts);
                if (uid == null || RecipeViewerEngine.isVanillaType(uid)) continue;
                bookDriven.computeIfAbsent(uid, k -> new ArrayList<>())
                        .add(RecipeViewerIndex.toIndexed(knownEntry));
            } catch (Exception | LinkageError e) {
                // one broken entry must not break the recipe-book pass
            }
        }
        for (Map.Entry<String, List<RecipeViewerEngine.IndexedRecipe>> e : bookDriven.entrySet()) {
            String uid = e.getKey();
            List<ItemStack> stations = stationsFor(Identifier.parse(uid), catalysts);
            RecipeViewerEngine.registerType(uid, e.getValue(), stations);
            // The JEI full collection may have registered the same type above
            // (recipe-book data wins in the engine, but the category tab is
            // created once — a duplicate TypeInfo would surface as a second
            // tab with the same name).
            boolean alreadyIndexed = typeInfos.stream().anyMatch(t -> t.uid().equals(uid));
            if (!alreadyIndexed) {
                IRecipeCategory<?> category = categoryByUid.get(uid);
                Component title = category != null ? category.getTitle() : Component.literal(uid);
                typeInfos.add(new TypeInfo(uid, title, stations));
            }
            typeCount++;
            recipeCount += e.getValue().size();
            BetterRecipeBook.LOGGER.info("[BRBE-JEI-Plugins] type {} driven by recipe book: {} unlocked recipes",
                    uid, e.getValue().size());
        }

        // Rebuild the recipe-book-backed workstation set: the vanilla types
        // (including external registrations, e.g. BetterEnd's end stone smelter
        // under minecraft:blasting) plus every recipe-book-driven mod type's
        // stations.  The "hide objects of workstations without a recipe book"
        // filter consults this set.
        List<ItemStack> bookStations = new ArrayList<>(RecipeViewerIndex.vanillaWorkstationItems());
        for (Map.Entry<String, List<RecipeViewerEngine.IndexedRecipe>> e : bookDriven.entrySet()) {
            bookStations.addAll(stationsFor(Identifier.parse(e.getKey()), catalysts));
        }
        RecipeViewerEngine.setRecipeBookStationItems(bookStations);
        // Category-tab visibility (categories whose objects are all hidden by
        // the filter) depends on the freshly registered engine data.
        RecipeViewerCategories.markVisibilityDirty();

        // Types with a recipe-book category but no recipe-book UI (e.g. bclib
        // registers a RecipeBookCategory while its anvils/alloying furnace have
        // no recipe book) keep the original JEI full-collection path — a
        // recipe-book category registration alone is NOT evidence of a recipe
        // book, so nothing is hidden here.  The only authoritative signal is
        // the known set itself: a type's entries appearing there means the
        // server treats it as recipe-book backed (bookDriven above).

        List<RecipeViewerCategory> dynamicCategories = new ArrayList<>();
        for (List<TypeInfo> group : groupBySharedStation(typeInfos)) {
            List<String> uids = new ArrayList<>();
            List<ItemStack> stations = new ArrayList<>();
            Component title = null;
            for (TypeInfo info : group) {
                // Dedupe: one type can legally appear once per group (the
                // recipe-book pass re-registers a type the JEI pass already
                // indexed) — a duplicated uid would double every query.
                if (!uids.contains(info.uid())) uids.add(info.uid());
                stations.addAll(info.stations());
                if (title == null && !info.stations().isEmpty()) {
                    title = info.title();
                }
            }
            if (title == null) title = group.get(0).title();
            dynamicCategories.add(new PluginRecipeViewerCategory(uids, title, stations));
        }

        RecipeViewerCategories.registerExternal(dynamicCategories);
        BetterRecipeBook.LOGGER.info("[BRBE-JEI-Plugins] indexed {} types / {} recipes; {} dynamic categories ({} merged)",
                typeCount, recipeCount, dynamicCategories.size(), typeInfos.size() - dynamicCategories.size());
    }

    /** A type-local identity for a recipe: its input and product item ids,
     *  sorted and joined.  Equal pairs are treated as duplicate recipes. */
    private static String fingerprint(List<ItemStack> inputs, List<ItemStack> products) {
        StringBuilder sb = new StringBuilder();
        appendSortedItemIds(sb, inputs);
        sb.append('|');
        appendSortedItemIds(sb, products);
        return sb.toString();
    }

    private static void appendSortedItemIds(StringBuilder sb, List<ItemStack> stacks) {
        List<String> names = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                names.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            }
        }
        names.sort(null);
        for (String name : names) {
            sb.append(name).append(';');
        }
    }

    /** Group recipe types that share any workstation block (JEI's catalyst
     *  semantics: one station can drive several recipe types).  Types with no
     *  station stay unmerged. */
    private static List<List<TypeInfo>> groupBySharedStation(List<TypeInfo> typeInfos) {
        int n = typeInfos.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (sharesStation(typeInfos.get(i), typeInfos.get(j))) {
                    union(parent, i, j);
                }
            }
        }
        Map<Integer, List<TypeInfo>> groups = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            int root = find(parent, i);
            groups.computeIfAbsent(root, k -> new ArrayList<>()).add(typeInfos.get(i));
        }
        return new ArrayList<>(groups.values());
    }

    private static boolean sharesStation(TypeInfo a, TypeInfo b) {
        if (a.stationIds().isEmpty() || b.stationIds().isEmpty()) return false;
        Set<Identifier> aIds = a.stationIds();
        for (Identifier id : b.stationIds()) {
            if (aIds.contains(id)) return true;
        }
        return false;
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a);
        int rb = find(parent, b);
        if (ra != rb) parent[ra] = rb;
    }

    /** The JEI recipe type whose catalysts contain {@code station}, or null.
     *  This is the reverse of the catalysts map ({@code type -> station items})
     *  and ties a recipe-book entry's declared crafting station to its type. */
    private static String typeUidForStation(ItemStack station, Map<Identifier, Set<Identifier>> catalysts) {
        if (station == null || station.isEmpty()) return null;
        Identifier stationId = BuiltInRegistries.ITEM.getKey(station.getItem());
        if (stationId == null) return null;
        for (Map.Entry<Identifier, Set<Identifier>> entry : catalysts.entrySet()) {
            if (entry.getValue() != null && entry.getValue().contains(stationId)) {
                return entry.getKey().toString();
            }
        }
        return null;
    }

    private static List<ItemStack> stationsFor(Identifier recipeTypeUid, Map<Identifier, Set<Identifier>> catalysts) {
        List<ItemStack> out = new ArrayList<>();
        Set<Identifier> itemIds = catalysts.get(recipeTypeUid);
        if (itemIds == null) return out;
        for (Identifier id : itemIds) {
            BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> out.add(new ItemStack(item)));
        }
        return out;
    }

    /** Convert a recipe's extracted slots into a native layout (visible,
     *  non-render-only slots with their positions), for hover rendering. */
    private static RecipeViewerEngine.RecipeLayout toLayout(DataOnlyLayoutBuilder builder, List<SlotData> slots,
                                                            RecipeViewerEngine.RecipeBackground background) {
        List<RecipeViewerEngine.RecipeSlotLayout> layoutSlots = new ArrayList<>();
        for (SlotData slot : slots) {
            if (slot.role() == RecipeIngredientRole.RENDER_ONLY) continue;
            if (!slot.visible()) continue;
            if (slot.stacks().isEmpty()) continue;
            layoutSlots.add(new RecipeViewerEngine.RecipeSlotLayout(slot.x(), slot.y(),
                    slot.role().ordinal(), slot.stacks()));
        }
        return new RecipeViewerEngine.RecipeLayout(builder.width(), builder.height(), layoutSlots, background);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void runSetRecipe(IRecipeCategory category, Object recipe, DataOnlyLayoutBuilder builder) {
        category.setRecipe(builder, recipe, EmptyFocusGroup.INSTANCE);
    }
}

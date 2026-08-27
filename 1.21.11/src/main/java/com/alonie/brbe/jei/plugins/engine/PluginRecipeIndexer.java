package com.alonie.brbe.jei.plugins.engine;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.recipeviewer.RecipeViewerCategories;
import com.alonie.brbe.recipeviewer.RecipeViewerCategory;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
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

    /** A JEI full-collection recipe's native render info, kept so recipe-book
     *  driven entries (which replace the synthetic entries in the engine) can
     *  be matched back to the mod's complete JEI UI by result. */
    private record RenderCandidate(RecipeViewerEngine.RecipeLayout layout,
                                   List<ItemStack> products,
                                   IRecipeCategory<?> category, Object recipe) {}

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
        Map<String, List<RenderCandidate>> renderCandidates = new HashMap<>();

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
                indexPluginRecipe(category, recipe, uid, stations, background,
                        seenRecipes, indexed, renderCandidates, false);
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
            // Session-persistent: once a type is driven by its recipe book it
            // stays a recipe-book type, so a later re-collection with an
            // unsynced / empty known set cannot drop its workstations from the
            // legal set (cooking pot must stay legal even at zero unlocks).
            RecipeViewerEngine.registerRecipeBookType(uid);
            typeCount++;
            recipeCount += e.getValue().size();
            BetterRecipeBook.LOGGER.info("[BRBE-JEI-Plugins] type {} driven by recipe book: {} unlocked recipes",
                    uid, e.getValue().size());
        }

        // Rebuild the recipe-book-backed workstation set: the vanilla types'
        // OWN stations (minecraft-namespace blocks only, via
        // vanillaWorkstationItems) plus every recipe-book-driven mod type's
        // stations.  A mod block registered under a vanilla type (e.g.
        // BetterEnd's end stone smelter under minecraft:blasting) is NOT a
        // recipe-book workstation — it has no recipe book of its own — so it
        // must not enter the set, or the "hide objects of workstations without
        // a recipe book" filter would keep its objects.
        List<ItemStack> bookStations = new ArrayList<>(RecipeViewerIndex.vanillaWorkstationItems());
        for (Map.Entry<Identifier, Set<Identifier>> c : catalysts.entrySet()) {
            String uid = c.getKey().toString();
            if (RecipeViewerEngine.isVanillaType(uid)) continue;
            if (!RecipeViewerEngine.isRecipeBookType(uid) || c.getValue() == null) continue;
            for (Identifier itemId : c.getValue()) {
                BuiltInRegistries.ITEM.getOptional(itemId).ifPresent(item -> bookStations.add(new ItemStack(item)));
            }
        }
        RecipeViewerEngine.setRecipeBookStationItems(bookStations);
        BetterRecipeBook.LOGGER.info("[BRBE-JEI-Plugins] recipe-book workstation set: {} items",
                bookStations.size());
        // Attach the JEI full-collection native layouts and render entries to
        // the recipe-book driven entries (matched by result): the popup / pin
        // rendering then shows the mod's complete JEI UI (native layout +
        // background texture) instead of the vanilla crafting fallback.
        for (Map.Entry<String, List<RecipeViewerEngine.IndexedRecipe>> e : bookDriven.entrySet()) {
            List<RenderCandidate> candidates = renderCandidates.get(e.getKey());
            if (candidates == null || candidates.isEmpty()) continue;
            for (RecipeViewerEngine.IndexedRecipe indexed : e.getValue()) {
                RecipeDisplayId knownId = indexed.entry().id();
                if (renderEntryFor(knownId) != null) continue;
                List<ItemStack> results = indexed.outputs();
                for (RenderCandidate candidate : candidates) {
                    if (resultsOverlap(results, candidate.products())) {
                        RecipeViewerEngine.registerLayout(knownId, candidate.layout());
                        RENDER_ENTRIES.put(knownId, new RenderEntry(candidate.category(), candidate.recipe()));
                        break;
                    }
                }
            }
        }
        // Vanilla stonecutter / smithing: the same treatment for the vanilla
        // recipe-book entries the engine sources from ClientRecipeBook.known —
        // they get the vanilla JEI category's native layout and render entry,
        // so their popup / pin renders the full JEI UI (slot backgrounds +
        // arrow) exactly like JEI's own recipe view.
        attachVanillaCategoryLayouts();
        // Vanilla JEI plugin recipe types (anvil / brewing / grindstone): their
        // recipes are runtime-built by JEI's vanilla plugin (no datapack
        // holders), so they enter the engine here from the JEI manager, each
        // with its native layout + render entry (full JEI UI preview/pin).
        indexVanillaPluginTypes();
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

    /** The vanilla JEI recipe type ids whose popup previews should render the
     *  full JEI UI (the vanilla JEI categories' own layouts). */
    private static final List<String> VANILLA_JEI_LAYOUT_TYPES =
            List.of("minecraft:stonecutting", "minecraft:smithing");

    /** Attach the vanilla JEI layout + render entry to every engine entry of
     *  the stonecutter / smithing types: the entries are matched back to their
     *  {@link RecipeHolder} (by display equality against the server-synced
     *  recipe set), run through the vanilla JEI category's {@code setRecipe}
     *  and registered like the mod (synthetic) entries — so the popup / pin
     *  preview shows the complete JEI UI (JEI slot backgrounds, recipe arrow)
     *  instead of the vanilla fixed-pair layout.  Entries with no matchable
     *  holder keep the vanilla fallback rendering.
     *
     *  <p>Requires the JEI runtime ({@link JeiRuntimeBridge}) and the
     *  server-synced recipe map ({@code Internal.getClientSyncedRecipes});
     *  without either the pass is a no-op (the vanilla fallback stays in
     *  effect).</p>
     */
    private static void attachVanillaCategoryLayouts() {
        IRecipeManager manager = JeiRuntimeBridge.recipeManager();
        if (manager == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        // The server-synced recipe map (the same source the embedded headless
        // core's manager is fed with — vanilla and mod holders alike).
        RecipeMap recipeMap = mezz.jei.common.Internal.getClientSyncedRecipes();
        if (recipeMap == null || recipeMap.values().isEmpty()) return;
        List<RecipeHolder<?>> holders = new ArrayList<>(recipeMap.values());

        // The vanilla JEI categories (from the manager's own registry — the
        // headless embedded core and the real JEI both register them).
        Map<String, IRecipeCategory<?>> vanillaCategories = new HashMap<>();
        for (IRecipeCategory<?> category : manager.createRecipeCategoryLookup().get().toList()) {
            Identifier uid = category.getRecipeType().getUid();
            if (uid == null) continue;
            String uidStr = uid.toString();
            if (VANILLA_JEI_LAYOUT_TYPES.contains(uidStr)) {
                vanillaCategories.put(uidStr, category);
            }
        }
        if (vanillaCategories.isEmpty()) return;

        for (String uid : VANILLA_JEI_LAYOUT_TYPES) {
            attachVanillaCategoryType(vanillaCategories.get(uid), uid, holders);
        }
    }

    /** One vanilla type pass of {@link #attachVanillaCategoryLayouts}. */
    private static void attachVanillaCategoryType(IRecipeCategory<?> category, String uid,
                                                  List<RecipeHolder<?>> holders) {
        if (category == null) return;
        try {
            List<RecipeDisplayEntry> entries = new ArrayList<>();
            for (RecipeDisplayEntry entry : RecipeViewerEngine.allRecipes(uid)) {
                if (entry == null || renderEntryFor(entry.id()) != null) continue;
                if ("minecraft:stonecutting".equals(uid)
                        && !(entry.display() instanceof net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay)) continue;
                if ("minecraft:smithing".equals(uid)
                        && !(entry.display() instanceof net.minecraft.world.item.crafting.display.SmithingRecipeDisplay)) continue;
                entries.add(entry);
            }
            if (entries.isEmpty()) return;
            List<RecipeHolder<?>> candidates = holderCandidates(uid, holders);
            if (candidates.isEmpty()) return;

            int attached = 0;
            for (RecipeDisplayEntry entry : entries) {
                RecipeHolder<?> holder = findMatchingHolder(entry, candidates);
                if (holder == null) continue;
                try {
                    DataOnlyLayoutBuilder builder =
                            new DataOnlyLayoutBuilder(category.getWidth(), category.getHeight());
                    runSetRecipe(category, holder, builder);
                    RecipeViewerEngine.RecipeLayout layout = toLayout(builder, builder.slotData(), null);
                    if (layout == null || layout.slots().isEmpty()) continue;
                    RecipeViewerEngine.registerLayout(entry.id(), layout);
                    RENDER_ENTRIES.put(entry.id(), new RenderEntry(category, holder));
                    attached++;
                } catch (Exception | LinkageError ex) {
                    BetterRecipeBook.LOGGER.warn("[BRBE-JEI-Plugins] failed to attach vanilla {} layout: {}",
                            uid, ex.toString());
                }
            }
            BetterRecipeBook.LOGGER.info("[BRBE-JEI-Plugins] vanilla {}: {} recipe previews switched to JEI UI",
                    uid, attached);
        } catch (Exception | LinkageError e) {
            BetterRecipeBook.LOGGER.warn("[BRBE-JEI-Plugins] vanilla {} layout pass failed: {}",
                    uid, e.toString());
        }
    }

    /** The vanilla JEI plugin recipe types whose recipes are runtime-built by
     *  JEI's own vanilla plugin (no datapack holders, not recipe-book entries):
     *  they are indexed into the query engine from the JEI manager, so their
     *  preview / pin render the complete JEI UI (native layout + render entry)
     *  like every mod category. */
    private static final List<String> VANILLA_PLUGIN_TYPES =
            List.of("minecraft:anvil", "minecraft:brewing", "minecraft:grindstone");

    /** Index the vanilla JEI plugin recipe types into the query engine: for
     *  each type the recipes are read from the JEI manager (the same data the
     *  vanilla plugin registered), run through the category's {@code setRecipe}
     *  and registered like the mod (synthetic) entries — layout + render entry
     *  attached, so preview / pin show the full JEI UI.  Requires the JEI
     *  runtime; without it the types are simply absent (their viewer categories
     *  have no content and stay hidden). */
    private static void indexVanillaPluginTypes() {
        IRecipeManager manager = JeiRuntimeBridge.recipeManager();
        if (manager == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        // These are no-recipe-book workstations (anvil / brewing stand /
        // grindstone): with the hide toggle on they are source-excluded exactly
        // like the stonecutter — their engine types are dropped (not just not
        // re-registered, or a pre-toggle type would linger and the
        // default-category pick would bypass the filter's station cut).
        if (BetterRecipeBook.config.hideNoRecipeBookStationObjects) {
            for (String uid : VANILLA_PLUGIN_TYPES) {
                RecipeViewerEngine.clearType(uid);
            }
            return;
        }
        Map<String, IRecipeCategory<?>> categoriesByUid = new HashMap<>();
        for (IRecipeCategory<?> category : manager.createRecipeCategoryLookup().get().toList()) {
            Identifier uid = category.getRecipeType().getUid();
            if (uid != null) categoriesByUid.put(uid.toString(), category);
        }
        for (String uid : VANILLA_PLUGIN_TYPES) {
            try {
                IRecipeCategory<?> category = categoriesByUid.get(uid);
                if (category == null) continue;
                List<ItemStack> stations = vanillaStationsFor(uid);
                List<?> recipes;
                try {
                    recipes = manager.createRecipeLookup(category.getRecipeType()).get().toList();
                } catch (Exception | LinkageError e) {
                    BetterRecipeBook.LOGGER.warn("[BRBE-JEI-Plugins] vanilla {} recipe lookup failed: {}",
                            uid, e.toString());
                    continue;
                }
                if (recipes.isEmpty()) continue;
                List<RecipeViewerEngine.IndexedRecipe> indexed = new ArrayList<>();
                Set<String> seenRecipes = new HashSet<>();
                for (Object recipe : recipes) {
                    indexPluginRecipe(category, recipe, uid, stations, null,
                            seenRecipes, indexed, null, true);
                }
                if (indexed.isEmpty()) continue;
                RecipeViewerEngine.registerType(uid, indexed, stations);
                BetterRecipeBook.LOGGER.info("[BRBE-JEI-Plugins] vanilla runtime type {}: {} recipes indexed (JEI UI)",
                        uid, indexed.size());
            } catch (Exception | LinkageError e) {
                BetterRecipeBook.LOGGER.warn("[BRBE-JEI-Plugins] vanilla runtime type {} pass failed: {}",
                        uid, e.toString());
            }
        }
    }

    /** Workstation block items of a vanilla JEI plugin type (the same stations
     *  JEI's {@code registerRecipeCatalysts} declares; all anvil variants). */
    private static List<ItemStack> vanillaStationsFor(String uid) {
        List<ItemStack> out = new ArrayList<>();
        switch (uid) {
            case "minecraft:anvil" -> {
                addStation(out, "minecraft:anvil");
                addStation(out, "minecraft:chipped_anvil");
                addStation(out, "minecraft:damaged_anvil");
            }
            case "minecraft:brewing" -> addStation(out, "minecraft:brewing_stand");
            case "minecraft:grindstone" -> addStation(out, "minecraft:grindstone");
            default -> { }
        }
        return out;
    }

    private static void addStation(List<ItemStack> out, String id) {
        BuiltInRegistries.ITEM.getOptional(Identifier.parse(id))
                .ifPresent(item -> out.add(new ItemStack(item)));
    }

    /** Index one plugin recipe into {@code indexed} (shared by the JEI full
     *  collection pass and the vanilla plugin types pass).  Extracts the
     *  recipe's slots through the category's {@code setRecipe}, synthesizes a
     *  display entry, registers its native layout + JEI render entry, and
     *  dedupes by (inputs, products) fingerprint.  {@code renderOnlyAsOutput}
     *  counts RENDER_ONLY slots as products (the vanilla grindstone declares
     *  its output slot RENDER_ONLY); mod recipes keep them data-only. */
    private static void indexPluginRecipe(IRecipeCategory<?> category, Object recipe, String uid,
                                          List<ItemStack> stations,
                                          RecipeViewerEngine.RecipeBackground background,
                                          Set<String> seenRecipes,
                                          List<RecipeViewerEngine.IndexedRecipe> indexed,
                                          Map<String, List<RenderCandidate>> renderCandidates,
                                          boolean renderOnlyAsOutput) {
        try {
            DataOnlyLayoutBuilder builder =
                    new DataOnlyLayoutBuilder(category.getWidth(), category.getHeight());
            runSetRecipe(category, recipe, builder);
            List<SlotData> slots = builder.slotData();
            List<ItemStack> inputs = new ArrayList<>();
            Map<Item, ItemStack> productsByItem = new LinkedHashMap<>();
            for (SlotData slot : slots) {
                if ((slot.role() == RecipeIngredientRole.OUTPUT
                        || (renderOnlyAsOutput && slot.role() == RecipeIngredientRole.RENDER_ONLY))
                        && slot.visible()) {
                    // Player-obtainable products: every stack in a visible
                    // OUTPUT slot (or a render-only output), de-duplicated by
                    // item so variant stacks or repeated slots collapse into
                    // one product.  Invisible slots are data-only (not
                    // rendered, not a product) and are excluded, matching
                    // toLayout.
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
                return;
            }
            if (!seenRecipes.add(fingerprint(inputs, products))) {
                BetterRecipeBook.LOGGER.info("[BRBE-JEI-Plugins] duplicate {} recipe skipped", uid);
                return;
            }
            RecipeViewerEngine.RecipeLayout layout = toLayout(builder, slots, background);
            Object groupKey = recipe;
            RecipeDisplayEntry synthetic =
                    SyntheticRecipeDisplayEntryFactory.createForOutput(slots, stations, products);
            RENDER_ENTRIES.put(synthetic.id(), new RenderEntry(category, recipe));
            RecipeViewerEngine.registerLayout(synthetic.id(), layout);
            if (renderCandidates != null) {
                renderCandidates.computeIfAbsent(uid, k -> new ArrayList<>())
                        .add(new RenderCandidate(layout, products, category, recipe));
            }
            indexed.add(new RecipeViewerEngine.IndexedRecipe(synthetic, inputs, products, groupKey));
        } catch (Exception | LinkageError ex) {
            BetterRecipeBook.LOGGER.warn("[BRBE-JEI-Plugins] failed to index a {} recipe: {}",
                    uid, ex.toString());
        }
    }

    /** The server-synced holders of one vanilla recipe type. */
    private static List<RecipeHolder<?>> holderCandidates(String uid, List<RecipeHolder<?>> holders) {
        boolean stonecutting = "minecraft:stonecutting".equals(uid);
        List<RecipeHolder<?>> out = new ArrayList<>();
        for (RecipeHolder<?> holder : holders) {
            if (holder == null || holder.value() == null) continue;
            if (stonecutting ? holder.value() instanceof StonecutterRecipe
                    : holder.value() instanceof SmithingRecipe) {
                out.add(holder);
            }
        }
        return out;
    }

    /** The holder whose display equals {@code entry}'s display (value equality:
     *  the server-synced displays and the recipe-book entries come from the
     *  same datapack data), or null when no holder matches. */
    private static RecipeHolder<?> findMatchingHolder(RecipeDisplayEntry entry,
                                                      List<RecipeHolder<?>> candidates) {
        RecipeDisplay target = entry.display();
        if (target == null) return null;
        for (RecipeHolder<?> holder : candidates) {
            try {
                for (RecipeDisplay display : holder.value().display()) {
                    if (target.equals(display)) return holder;
                }
            } catch (Exception | LinkageError ignored) {
                // one broken holder must not break the whole pass
            }
        }
        return null;
    }

    /** A type-local identity for a recipe: its input and product stacks
     *  (item id AND complete component data), sorted and joined.  Equal pairs
     *  are treated as duplicate recipes — only identical stacks collapse, so
     *  recipes whose products share an item but differ in components (three
     *  enchanted books with different enchantments, potions, …) stay distinct.
     *  Better Archeology's identifying recipes are exactly this shape: three
     *  {@code identifying} recipes all output {@code enchanted_book}, and the
     *  item-id-only fingerprint used to merge them into one. */
    private static String fingerprint(List<ItemStack> inputs, List<ItemStack> products) {
        StringBuilder sb = new StringBuilder();
        appendSortedItemKeys(sb, inputs);
        sb.append('|');
        appendSortedItemKeys(sb, products);
        return sb.toString();
    }

    private static void appendSortedItemKeys(StringBuilder sb, List<ItemStack> stacks) {
        List<String> keys = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                keys.add(stackKey(stack));
            }
        }
        keys.sort(null);
        for (String key : keys) {
            sb.append(key).append(';');
        }
    }

    /** A value identity for one stack: item id plus its full component data
     *  (enchantments, potion contents, …) via
     *  {@link net.minecraft.core.component.DataComponentPatch}'s record-style
     *  description.  Two stacks of the same item with different components
     *  produce different keys, so recipes that share an output item but differ
     *  in components never collapse. */
    private static String stackKey(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "#"
                + stack.getComponentsPatch();
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

    /** Whether the two result lists share at least one item (result match). */
    private static boolean resultsOverlap(List<ItemStack> a, List<ItemStack> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        for (ItemStack sa : a) {
            if (sa == null || sa.isEmpty()) continue;
            for (ItemStack sb : b) {
                if (sb != null && !sb.isEmpty() && sa.getItem() == sb.getItem()) return true;
            }
        }
        return false;
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

package com.alonie.brbe.cache;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.ClientRecipeBookAccessor;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

    /** The recipe book's known display entries, or empty if unavailable.  The
     *  known set is the server-synced <b>unlocked</b> subset of the recipe
     *  book — the authoritative "recipe book data" for every recipe-book
     *  backed category (vanilla and mod alike). */
    public static List<RecipeDisplayEntry> knownEntries() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return List.of();
        Map<RecipeDisplayId, RecipeDisplayEntry> known =
                ((ClientRecipeBookAccessor) mc.player.getRecipeBook()).brbe$getKnown();
        return known == null ? List.of() : List.copyOf(known.values());
    }

    /** The crafting-station item of {@code entry}'s display (e.g. the
     *  cooking pot for a Farmer's Delight cooking recipe), or empty when the
     *  display declares none (crafting-table recipes use the empty slot
     *  display).  This is the bridge that ties a recipe-book category to its
     *  JEI recipe type: a station block registered as that type's catalyst. */
    public static ItemStack resolveCraftingStation(RecipeDisplayEntry entry) {
        if (entry == null) return ItemStack.EMPTY;
        try {
            List<ItemStack> stations = resolveSlotDisplay(entry.display().craftingStation());
            if (stations != null) {
                for (ItemStack station : stations) {
                    if (station != null && !station.isEmpty()) return station;
                }
            }
        } catch (Exception e) {
            // unresolvable crafting station — no station
        }
        return ItemStack.EMPTY;
    }

    /** Rebuild the query engine's indices from the vanilla known set — one type
     *  per JEI recipe type ({@code minecraft:smelting}, {@code minecraft:blasting},
     *  … each its own type so the furnace category can aggregate them with dedup).
     *  Called after the recipe book's known set is (re)populated. */
    public static void rebuildEngine() {
        // Throttle: rebuildCollections fires repeatedly on item pickup /
        // progression unlocks (a single pickup can unlock several recipes,
        // each calling rebuildCollections → rebuildEngine).  Every call would
        // rebuild the whole engine (iterating every known entry + re-running
        // the JEI collection).  Coalesce: mark dirty and let the tick-end
        // flush rebuild once with the final known set.
        engineDirty = true;
    }

    /** Rebuild now if a rebuild was requested since the last flush.  Called
     *  from the client tick-end hook so a burst of rebuildCollections calls
     *  within one tick collapses into a single full rebuild. */
    public static void flushEngineRebuildIfDirty() {
        if (!engineDirty) {
            return;
        }
        engineDirty = false;
        // Skip when the known set has not actually changed since the last
        // rebuild (defense in depth against unchanged storms).
        List<RecipeDisplayEntry> known = knownEntries();
        int fingerprint = knownFingerprint(known);
        if (fingerprint == lastRebuildFingerprint && !forceRebuild) {
            return;
        }
        forceRebuild = false;
        lastRebuildFingerprint = fingerprint;
        rebuildEngineInternal(known);
    }

    private static boolean engineDirty;

    /** Force the next rebuildEngine call to run even if the known set is
     *  unchanged (used by config hot-reload paths). */
    public static void forceNextRebuild() {
        forceRebuild = true;
        engineDirty = true;
    }

    private static int lastRebuildFingerprint = Integer.MIN_VALUE;
    private static boolean forceRebuild;

    /** A cheap identity hash of the known set (entry ids in insertion order),
     *  so unchanged rebuildCollections storms are detected in O(n) without
     *  hashing the full display data. */
    private static int knownFingerprint(List<RecipeDisplayEntry> known) {
        int hash = 0;
        for (RecipeDisplayEntry entry : known) {
            hash = hash * 31 + entry.id().index();
        }
        return hash;
    }

    private static void rebuildEngineInternal(List<RecipeDisplayEntry> knownEntries) {
        RecipeViewerEngine.clearVanilla();
        Map<String, List<RecipeViewerEngine.IndexedRecipe>> grouped = new LinkedHashMap<>();
        Map<String, List<ItemStack>> stationItems = new LinkedHashMap<>();
        // Every workstation's block items keyed by its type id.  Built-in and
        // external stations sharing a type (e.g. the blast furnace and a mod
        // smelter both serving minecraft:blasting) all contribute their items,
        // so a usage query on any of them returns the whole type (JEI
        // semantics).  Collected independently of the entry loop below: an
        // entry only ever falls into one type, but a type can be served by
        // several workstation registrations.
        for (Workstation station : workstations()) {
            stationItems.computeIfAbsent(station.typeId(), k -> new ArrayList<>())
                    .addAll(java.util.Arrays.asList(station.fallbackIcons()));
        }
        Map<String, Integer> categoryCounts = new java.util.TreeMap<>();
        int unmatched = 0;
        for (RecipeDisplayEntry entry : knownEntries) {
            String path = categoryPath(entry);
            categoryCounts.merge(path.isEmpty() ? "(empty)" : path, 1, Integer::sum);
            boolean matched = false;
            for (Workstation station : workstations()) {
                if (!station.matchesPath(path)) continue;
                String uid = station.typeId();
                grouped.computeIfAbsent(uid, k -> new ArrayList<>())
                        .add(new RecipeViewerEngine.IndexedRecipe(entry, inputStacks(entry), outputStacks(entry)));
                matched = true;
                break;
            }
            if (!matched) unmatched++;
        }
        BetterRecipeBook.LOGGER.info("[BRBE] rebuildEngine known-by-category: {} unmatched={}",
                categoryCounts, unmatched);
        for (Map.Entry<String, List<RecipeViewerEngine.IndexedRecipe>> e : grouped.entrySet()) {
            // 切石/锻造：条目与 layout 由 headless-jei（JEI 运行时）提供
            // （其条目带原生 layout，弹窗可委托完整 JEI UI）——这里跳过，
            // 避免与 headless 重复注册（同 uid 后注册者覆盖前者）。
            if (e.getKey().equals("minecraft:stonecutting")
                    || e.getKey().equals("minecraft:smithing")) {
                continue;
            }
            RecipeViewerEngine.registerType(e.getKey(), e.getValue(), stationItems.get(e.getKey()));
        }
        BetterRecipeBook.LOGGER.info("[BRBE] rebuildEngine: {} types, {} entries",
                grouped.size(), grouped.values().stream().mapToInt(List::size).sum());
        RecipeViewerEngine.notifyRebuilt();
    }

    /** Wrap a known display entry as an engine index entry (inputs/outputs
     *  extracted exactly like the vanilla rebuild does).  Used by the JEI
     *  plugin collector to register recipe-book-driven mod types from the
     *  known set. */
    public static RecipeViewerEngine.IndexedRecipe toIndexed(RecipeDisplayEntry entry) {
        return new RecipeViewerEngine.IndexedRecipe(entry, inputStacks(entry), outputStacks(entry));
    }

    /** Output item stacks of {@code entry} (its results). */
    private static List<ItemStack> outputStacks(RecipeDisplayEntry entry) {
        try {
            return entry.resultItems(null);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Input item stacks of {@code entry}, dispatched by display type: furnace
     *  uses its ingredient, stonecutter its input, smithing its
     *  template/base/addition, and everything else (crafting / synthetic) its
     *  crafting requirements. */
    private static List<ItemStack> inputStacks(RecipeDisplayEntry entry) {
        try {
            if (entry.display() instanceof FurnaceRecipeDisplay furnace) {
                return resolveSlotDisplay(furnace.ingredient());
            }
            if (entry.display() instanceof StonecutterRecipeDisplay stonecutter) {
                return resolveSlotDisplay(stonecutter.input());
            }
            if (entry.display() instanceof SmithingRecipeDisplay smithing) {
                List<ItemStack> out = new ArrayList<>();
                for (SlotDisplay slot : List.of(smithing.template(), smithing.base(), smithing.addition())) {
                    out.addAll(resolveSlotDisplay(slot));
                }
                return out;
            }
        } catch (Exception ignored) {
            // fall through to crafting requirements
        }
        Optional<List<Ingredient>> requirements = entry.craftingRequirements();
        if (requirements.isPresent()) {
            List<ItemStack> out = new ArrayList<>();
            for (Ingredient ingredient : requirements.get()) {
                ingredient.items().forEach(holder -> out.add(new ItemStack(holder.value())));
            }
            return out;
        }
        return List.of();
    }

    /** Recipe-book category family of a workstation.  The four furnace-family
     *  stations share {@link #FURNACE}; every other station maps one-to-one. */
    public enum Family { CRAFTING, FURNACE, STONECUTTING, SMITHING,
                          ANVIL, BREWING, GRINDSTONE, COMPOSTING }

    /**
     * A workstation: a self-owned identity string (equal to the JEI recipe-type
     *  id where the two overlap), the recipe-book category paths its recipes
     *  live under, and its block items.  Recipe entries carry their own
     *  {@link RecipeDisplayEntry#category()}, so "station → recipes" matches
     *  each entry's category path at query time — the old type-id → prefix
     *  switch is gone, its data lives here.
     */
    private record Workstation(Family family, String typeId,
                               List<String> categoryPrefixes,
                               List<Identifier> stationItems,
                               boolean recipeBook) {

        /** Whether {@code path} is a recipe-book category path of this station.
         *  A prefix ending in "_" matches by prefix ({@code crafting_}/
         *  {@code furnace_}/…); any other entry is a full category path matched
         *  exactly ({@code campfire}/{@code stonecutter}/{@code smithing}). */
        boolean matchesPath(String path) {
            for (String prefix : categoryPrefixes) {
                boolean match = prefix.endsWith("_")
                        ? path.startsWith(prefix)
                        : path.equals(prefix);
                if (match) return true;
            }
            return false;
        }

        /** Whether {@code item} is one of this workstation's block items. */
        boolean hasItem(Item item) {
            return stationItems.contains(BuiltInRegistries.ITEM.getKey(item));
        }

        /** The workstation's block items as icons. */
        ItemStack[] fallbackIcons() {
            List<ItemStack> icons = new ArrayList<>();
            for (Identifier id : stationItems) {
                BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> icons.add(new ItemStack(item)));
            }
            return icons.toArray(new ItemStack[0]);
        }
    }

    /** Built-in workstation registry: vanilla stations only.  Mod stations
     *  (e.g. the Farmer's Delight skillet) are supplied by the companion
     *  {@code brbe-jei-plugins} mod scanning each mod's JEI plugin, or appended
     *  manually via {@code config/brbe_workstations.json} (see
     *  {@link #workstations()}).  Recognition is self-contained — no runtime
     *  JEI data.
     *
     *  <p>The {@code recipeBook} flag is false for the stonecutter: vanilla's
     *  stonecutter has no recipe-book UI (its menu/screen are not recipe-book
     *  based), and BRBE provides none — so with "hide objects of workstations
     *  without a recipe book" on, the stonecutter is excluded from the whole
     *  query system (like a no-book mod station), rather than being treated as
     *  a recipe-book-backed builtin.</p> */
    private static final List<Workstation> BUILTIN_WORKSTATIONS = List.of(
            new Workstation(Family.CRAFTING, "minecraft:crafting", List.of("crafting_"),
                    List.of(Identifier.withDefaultNamespace("crafting_table"),
                            Identifier.withDefaultNamespace("crafter")), true),
            new Workstation(Family.FURNACE, "minecraft:smelting", List.of("furnace_"),
                    List.of(Identifier.withDefaultNamespace("furnace")), true),
            new Workstation(Family.FURNACE, "minecraft:blasting", List.of("blast_furnace_"),
                    List.of(Identifier.withDefaultNamespace("blast_furnace")), true),
            new Workstation(Family.FURNACE, "minecraft:smoking", List.of("smoker_"),
                    List.of(Identifier.withDefaultNamespace("smoker")), true),
            new Workstation(Family.FURNACE, "minecraft:campfire_cooking", List.of("campfire"),
                    List.of(Identifier.withDefaultNamespace("campfire"),
                            Identifier.withDefaultNamespace("soul_campfire")), true),
            new Workstation(Family.STONECUTTING, "minecraft:stonecutting", List.of("stonecutter"),
                    List.of(Identifier.withDefaultNamespace("stonecutter")), false),
            new Workstation(Family.SMITHING, "minecraft:smithing", List.of("smithing"),
                    List.of(Identifier.withDefaultNamespace("smithing_table")), true),
            // No-recipe-book vanilla workstations (JEI runtime recipe types):
            // their viewer categories (anvil / brewing / grindstone / compost)
            // are BRBE-built, and with the hide toggle on they are excluded
            // from the whole query system, exactly like the stonecutter.
            new Workstation(Family.ANVIL, "minecraft:anvil", List.of("anvil"),
                    List.of(Identifier.withDefaultNamespace("anvil"),
                            Identifier.withDefaultNamespace("chipped_anvil"),
                            Identifier.withDefaultNamespace("damaged_anvil")), false),
            new Workstation(Family.BREWING, "minecraft:brewing", List.of("brewing"),
                    List.of(Identifier.withDefaultNamespace("brewing_stand")), false),
            new Workstation(Family.GRINDSTONE, "minecraft:grindstone", List.of("grindstone"),
                    List.of(Identifier.withDefaultNamespace("grindstone")), false),
            new Workstation(Family.COMPOSTING, "minecraft:compostable", List.of("compost"),
                    List.of(Identifier.withDefaultNamespace("composter")), false));

    /** Effective registry: built-ins plus any stations appended from
     *  {@code config/brbe_workstations.json} or registered programmatically
     *  (the {@code brbe-jei-plugins} companion mod).  Built lazily on first
     *  query so the config file is read only after the client is up. */
    private static volatile List<Workstation> WORKSTATIONS;

    /** Block item ids of the built-in vanilla workstations — the dedupe key
     *  for external station registration.  Vanilla stations are already in the
     *  builtin registry and must not be re-added from the registry: the vanilla
     *  JEI runtime types (anvil / brewing / grindstone) push the same vanilla
     *  stations into {@code JeiRecipeRegistry.stations}, which
     *  {@code BrbeJeiBridge.importVanillaStationSpecs} would otherwise
     *  duplicate. */
    public static Set<Identifier> builtinWorkstationItemIds() {
        Set<Identifier> out = new HashSet<>();
        for (Workstation station : BUILTIN_WORKSTATIONS) {
            out.addAll(station.stationItems);
        }
        return out;
    }

    /** Programmatically registered external stations, injected before the
     *  registry is first built (or rebuilt immediately if it already was).
     *  Guarded by {@code RecipeViewerIndex.class}. */
    private static final List<WorkstationSpec> EXTERNAL_SPECS = new ArrayList<>();

    private static List<Workstation> workstations() {
        List<Workstation> stations = WORKSTATIONS;
        if (stations == null) {
            synchronized (RecipeViewerIndex.class) {
                stations = WORKSTATIONS;
                if (stations == null) {
                    stations = buildWorkstations();
                    WORKSTATIONS = stations;
                }
            }
        }
        // Source-level exclusion: with "hide objects of workstations without a
        // recipe book" on, a workstation without its own recipe book (every
        // config/external mod station — e.g. BetterEnd's end stone smelter —
        // and the vanilla stonecutter, which has no recipe-book UI) is dropped
        // from the whole system up front: it stops matching any query target,
        // its category tabs and objects never surface, and the hide filter
        // downstream has nothing left to judge.  Recipe-book-backed built-in
        // stations keep their recipeBook=true and stay.  (Mirrors 26.2.)
        if (BetterRecipeBook.config.hideNoRecipeBookStationObjects) {
            return stations.stream()
                    .filter(Workstation::recipeBook)
                    .toList();
        }
        return stations;
    }

    /** Workstation block items (registry order) of a built-in family — the
     *  viewer's left station-column list.  Already honouring the
     *  hide-no-recipe-book-station cut (via {@link #workstations()}), e.g. the
     *  FURNACE family lists furnace / blast_furnace / smoker / campfire /
     *  soul_campfire in one column. */
    public static List<ItemStack> workstationItems(Family family) {
        List<ItemStack> out = new ArrayList<>();
        for (Workstation ws : workstations()) {
            if (ws.family() == family) {
                for (ItemStack icon : ws.fallbackIcons()) {
                    out.add(icon);
                }
            }
        }
        return out;
    }

    private static List<Workstation> buildWorkstations() {
        List<Workstation> builtin = BUILTIN_WORKSTATIONS;
        List<Workstation> config = loadConfigWorkstations();
        List<Workstation> external = loadExternalWorkstations();
        BetterRecipeBook.LOGGER.info("[BRBE] buildWorkstations: builtin={} config={} external={}",
                builtin.size(), config.size(), external.size());
        List<Workstation> out = new ArrayList<>(builtin);
        out.addAll(config);
        out.addAll(external);
        return List.copyOf(out);
    }

    /** JSON DTO for {@code config/brbe_workstations.json}. */
    private static final class WorkstationFile {
        List<StationEntry> workstations = List.of();
    }

    private static final class StationEntry {
        String family;              // "FURNACE" / "CRAFTING" / "STONECUTTING" / "SMITHING"
        String typeId;              // optional identity string (shown in tooltips)
        List<String> categoryPrefixes = List.of();  // recipe-book category path prefixes
        List<String> items = List.of();             // "namespace:block" workstation blocks
    }

    /** Workstations appended by the modpack author, from
     *  {@code config/brbe_workstations.json}; empty when absent/invalid.
     *  Format:
     *  <pre>
     *  { "workstations": [ {
     *      "family": "FURNACE",
     *      "typeId": "mymod:kiln_smelting",
     *      "categoryPrefixes": ["mymod_furnace_"],
     *      "items": ["mymod:kiln"]
     *  } ] }
     *  </pre>
     *  A {@code categoryPrefixes} entry ending in "_" matches category paths by
     *  prefix (like {@code furnace_}), anything else matches exactly. */
    private static List<Workstation> loadConfigWorkstations() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.gameDirectory == null) return List.of();
            Path file = mc.gameDirectory.toPath().resolve("config")
                    .resolve("brbe_workstations.json");
            if (!Files.exists(file)) return List.of();
            String json = Files.readString(file, StandardCharsets.UTF_8);
            WorkstationFile cfg = new Gson().fromJson(json, WorkstationFile.class);
            if (cfg == null || cfg.workstations == null) return List.of();
            List<Workstation> out = new ArrayList<>();
            for (StationEntry entry : cfg.workstations) {
                Workstation station = parseStationEntry(entry);
                if (station != null) out.add(station);
            }
            return out;
        } catch (Exception e) {
            BetterRecipeBook.LOGGER.warn("[BRBE] Failed to read config/brbe_workstations.json: {}", e.toString());
            return List.of();
        }
    }

    private static Workstation parseStationEntry(StationEntry entry) {
        if (entry == null) return null;
        Family family;
        try {
            family = Family.valueOf(entry.family == null ? "" : entry.family.toUpperCase());
        } catch (IllegalArgumentException e) {
            BetterRecipeBook.LOGGER.warn("[BRBE] brbe_workstations.json: unknown family '{}'", entry.family);
            return null;
        }
        List<String> prefixes = new ArrayList<>();
        if (entry.categoryPrefixes != null) {
            for (String prefix : entry.categoryPrefixes) {
                if (prefix != null && !prefix.isBlank()) prefixes.add(prefix);
            }
        }
        List<Identifier> items = new ArrayList<>();
        if (entry.items != null) {
            for (String item : entry.items) {
                try {
                    String[] parts = item.split(":", 2);
                    Identifier id = parts.length == 2
                            ? Identifier.fromNamespaceAndPath(parts[0], parts[1])
                            : Identifier.withDefaultNamespace(parts[0]);
                    items.add(id);
                } catch (Exception e) {
                    BetterRecipeBook.LOGGER.warn("[BRBE] brbe_workstations.json: bad item id '{}'", item);
                }
            }
        }
        if (prefixes.isEmpty() || items.isEmpty()) {
            BetterRecipeBook.LOGGER.warn("[BRBE] brbe_workstations.json: entry needs non-empty categoryPrefixes and items, skipping");
            return null;
        }
        // Manually configured workstations have no recipe book of their own.
        return new Workstation(family, entry.typeId == null ? "" : entry.typeId, prefixes, items, false);
    }

    /** Public station descriptor accepted by {@link #registerExternalWorkstations},
     *  used by the companion {@code brbe-jei-plugins} mod to inject stations
     *  collected from mod JEI plugins.  Mirrors the config file format:
     *  {@code family} is one of FURNACE/CRAFTING/STONECUTTING/SMITHING
     *  (case-insensitive); {@code items} entries may omit the namespace. */
    public record WorkstationSpec(String family, String typeId,
                                  List<String> categoryPrefixes, List<String> items) {}

    /** Registers externally collected stations (built-in + config + external
     *  three-source merge).  If the registry is already built it is rebuilt
     *  immediately; otherwise the specs are picked up on first build.
     *  Idempotent: an identical spec is not registered twice, and a re-register
     *  with the same {@code typeId} replaces (rather than accumulates) the old
     *  spec — world re-joins or a changed mod set refresh the station list. */
    public static void registerExternalWorkstations(List<WorkstationSpec> specs) {
        if (specs == null || specs.isEmpty()) return;
        synchronized (RecipeViewerIndex.class) {
            int added = 0;
            for (WorkstationSpec spec : specs) {
                if (spec == null) continue;
                if (spec.typeId() != null) {
                    EXTERNAL_SPECS.removeIf(existing -> spec.typeId().equals(existing.typeId()));
                }
                if (!EXTERNAL_SPECS.contains(spec)) {
                    EXTERNAL_SPECS.add(spec);
                    added++;
                }
            }
            BetterRecipeBook.LOGGER.info("[BRBE] registerExternalWorkstations: +{} total={} builtAlready={}",
                    added, EXTERNAL_SPECS.size(), WORKSTATIONS != null);
            if (WORKSTATIONS != null) {
                WORKSTATIONS = buildWorkstations();
            }
        }
    }

    private static List<Workstation> loadExternalWorkstations() {
        synchronized (RecipeViewerIndex.class) {
            if (EXTERNAL_SPECS.isEmpty()) return List.of();
            List<Workstation> out = new ArrayList<>();
            for (WorkstationSpec spec : EXTERNAL_SPECS) {
                Workstation station = parseWorkstationSpec(spec);
                if (station != null) out.add(station);
            }
            return out;
        }
    }

    private static Workstation parseWorkstationSpec(WorkstationSpec spec) {
        Family family;
        try {
            family = Family.valueOf(spec.family() == null ? "" : spec.family().toUpperCase());
        } catch (IllegalArgumentException e) {
            BetterRecipeBook.LOGGER.warn("[BRBE] external workstation: unknown family '{}'", spec.family());
            return null;
        }
        List<String> prefixes = new ArrayList<>();
        if (spec.categoryPrefixes() != null) {
            for (String prefix : spec.categoryPrefixes()) {
                if (prefix != null && !prefix.isBlank()) prefixes.add(prefix);
            }
        }
        List<Identifier> items = new ArrayList<>();
        if (spec.items() != null) {
            for (String item : spec.items()) {
                try {
                    String[] parts = item.split(":", 2);
                    Identifier id = parts.length == 2
                            ? Identifier.fromNamespaceAndPath(parts[0], parts[1])
                            : Identifier.withDefaultNamespace(parts[0]);
                    items.add(id);
                } catch (Exception e) {
                    BetterRecipeBook.LOGGER.warn("[BRBE] external workstation: bad item id '{}'", item);
                }
            }
        }
        if (prefixes.isEmpty() || items.isEmpty()) {
            BetterRecipeBook.LOGGER.warn("[BRBE] external workstation: entry needs non-empty categoryPrefixes and items, skipping");
            return null;
        }
        // Recipe-book status of an external (mod) workstation, decided by the
        // vanilla type it is registered under.  Variant stations that reuse a
        // vanilla recipe-book screen (e.g. BetterEnd's jadestone furnaces
        // extend the vanilla furnace and open its recipe book) count as having
        // a recipe book; stations with a custom screen (e.g. BetterEnd's end
        // stone smelter, the only external registered under blasting today)
        // do not.  This is the single source of truth the "hide objects of
        // workstations without a recipe book" filter reads.
        boolean recipeBook = externalHasRecipeBook(
                spec.typeId() == null ? "" : spec.typeId());
        return new Workstation(family, spec.typeId() == null ? "" : spec.typeId(),
                prefixes, items, recipeBook);
    }

    /** Whether an external workstation registered under {@code typeId} opens
     *  a vanilla recipe-book screen (a variant of that vanilla workstation).
     *  Blasting is excluded: its only external registration in practice is
     *  BetterEnd's end stone smelter, which opens a custom screen. */
    private static boolean externalHasRecipeBook(String typeId) {
        if (typeId == null || typeId.isEmpty()) return false;
        return switch (typeId) {
            case "minecraft:smelting", "minecraft:smoking", "minecraft:campfire_cooking",
                 "minecraft:stonecutting", "minecraft:smithing", "minecraft:crafting" -> true;
            default -> false;
        };
    }

    /** The workstation {@code target} is, or null when it is not one
     *  (self-owned registry lookup — no runtime JEI data). */
    private static Workstation workstationFor(ItemStack target) {
        if (target == null || target.isEmpty()) return null;
        return workstationForItem(target.getItem());
    }

    private static Workstation workstationForItem(Item item) {
        for (Workstation station : workstations()) {
            if (station.hasItem(item)) return station;
        }
        return null;
    }

    /** Which workstation {@code target} is (self-owned registry lookup), or
     *  null when it is not a workstation. */
    public static String stationTypeIdFor(ItemStack target) {
        Workstation station = workstationFor(target);
        return station == null ? null : station.typeId();
    }

    /** Whether {@code target} is a furnace-family workstation (furnace / blast
     *  furnace / smoker / campfire / a mod furnace-family station). */
    public static boolean isFurnaceStation(ItemStack target) {
        Workstation station = workstationFor(target);
        return station != null && station.family() == Family.FURNACE;
    }

    /** Canonical key of a furnace recipe's content: the sorted smelted
     *  ingredients and sorted results.  Identical across stations so the same
     *  recipe registered for furnace/smoker/campfire dedupes to one entry. */
    public static String furnaceContentKey(FurnaceRecipeDisplay display) {
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
     *  as {furnace, blast furnace, smoker, campfire}; 0 when that station has no
     *  recipe for this content (e.g. food has no blast-furnace variant). */
    public static int[] furnaceStationTicks(RecipeDisplayEntry sample) {
        int[] ticks = new int[4];
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
            } else if (path.equals("campfire")) {
                ticks[3] = display.duration();
            }
        }
        return ticks;
    }

    /** Workstation item icons — one representative per workstation — that can
     *  produce {@code entry}, including mod workstations from the external
     *  registry.  Empty when no workstation matches. */
    public static List<ItemStack> workstationsIconsFor(RecipeDisplayEntry entry) {
        return workstationsIconsForPath(categoryPath(entry));
    }

    /** Vanilla recipe-book workstation items: only the built-in vanilla
     *  stations ({@link Workstation#recipeBook()}).  Mod stations registered
     *  under a vanilla type (e.g. BetterEnd's end stone smelter under
     *  {@code minecraft:blasting}, Farmer's Delight's skillet under campfire)
     *  are NOT recipe-book workstations: they have no recipe book of their own,
     *  even though their recipes live in a vanilla recipe-book category. */
    public static List<ItemStack> vanillaWorkstationItems() {
        List<ItemStack> out = new ArrayList<>();
        for (Workstation station : workstations()) {
            if (!station.recipeBook()) continue;
            out.addAll(java.util.Arrays.asList(station.fallbackIcons()));
        }
        return out;
    }

    /** Workstation icons for one furnace subcategory prefix (e.g.
     *  {@code "furnace_"}, {@code "blast_furnace_"}, {@code "campfire"}). */
    public static List<ItemStack> workstationsIconsForPrefix(String categoryPrefix) {
        return workstationsIconsForPath(categoryPrefix);
    }

    /** Furnace subcategory prefixes in bottom-up column order — matches the
     *  furnace/fuel tooltip's subcategory rows (烧炼 / 熔炼 / 烟熏 / 营火). */
    private static final List<String> FURNACE_SUBCATEGORY_PREFIXES = List.of(
            "furnace_", "blast_furnace_", "smoker_", "campfire");

    /** The furnace-family station column for the viewer's left panel: grouped
     *  by subcategory and stacked bottom-up (smelting 烧炼 / blasting 熔炼 /
     *  smoking 烟熏 / campfire cooking 营火), each group preserving the same
     *  order as that subcategory's tooltip icon row (left-to-right).  A station
     *  matching several subcategories keeps its first (lowest) position. */
    public static List<ItemStack> furnaceStationColumnItems() {
        List<ItemStack> out = new ArrayList<>();
        java.util.Set<net.minecraft.world.item.Item> seen = new java.util.HashSet<>();
        for (String prefix : FURNACE_SUBCATEGORY_PREFIXES) {
            for (ItemStack icon : workstationsIconsForPrefix(prefix)) {
                if (seen.add(icon.getItem())) out.add(icon);
            }
        }
        return List.copyOf(out);
    }

    private static List<ItemStack> workstationsIconsForPath(String path) {
        List<ItemStack> icons = new ArrayList<>();
        for (Workstation station : workstations()) {
            if (station.matchesPath(path)) {
                // Every block of the workstation, not just one representative —
                // e.g. campfire + soul campfire + the mod's stove/skillet.
                for (ItemStack icon : station.fallbackIcons()) {
                    icons.add(icon);
                }
            }
        }
        return icons;
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

    /** Whether {@code entry} is a stonecutter recipe display. */
    public static StonecutterRecipeDisplay asStonecutter(RecipeDisplayEntry entry) {
        try {
            if (entry.display() instanceof StonecutterRecipeDisplay s) return s;
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /** Whether {@code entry} is a smithing recipe display. */
    public static SmithingRecipeDisplay asSmithing(RecipeDisplayEntry entry) {
        try {
            if (entry.display() instanceof SmithingRecipeDisplay s) return s;
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

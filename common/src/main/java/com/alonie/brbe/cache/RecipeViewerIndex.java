package com.alonie.brbe.cache;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeBookAccessor;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 1.21.1 版查询索引 —— 按 1.21.11/26.2 的 RecipeViewerIndex 结构重写（2026-08-29）。
 *
 * <p>数据源从「RecipeManager 全量」改为「配方书 known 集」：仅玩家配方书实际含有
 * （服务端同步解锁 + 本地注入缓存）的配方是候选——与 1.21.11 的 "only show unlocked
 * recipes" 意图一致；工作站从硬编码 switch 升级为 1.21.11 同款 {@link Workstation}
 * 注册表（Family/BUILTIN_WORKSTATIONS/config 文件/外部注入，recipeBook 标志驱动
 * hideNoRecipeBookStationObjects 源级过滤）；重建节流引入 dirty + known 指纹。</p>
 *
 * <p>与 1.21.11 的鸿沟（RecipeDisplayEntry/SlotDisplay 缺失）：条目为
 * {@link RecipeHolder}；分类路径从 RecipeType 推导（1.21.1 无 display.category）；
 * 无 known-entry id 结构（RecipeBook.known 是 {@code Set<ResourceLocation>}——经
 * {@link RecipeBookAccessor} 读取后按 RecipeManager 解析）。</p>
 */
public final class RecipeViewerIndex {

    private RecipeViewerIndex() {}

    // ── 重建节流（dirty + 指纹，1.21.11 语义） ────────────────────────────────

    private static boolean engineDirty = true;
    private static int lastRebuildFingerprint = Integer.MIN_VALUE;
    private static boolean forceRebuild;

    /** Mark the engine rebuild stale (recipes/known changed). */
    public static void markDirty() {
        engineDirty = true;
    }

    /** Force the next rebuild even if the known set is unchanged. */
    public static void forceNextRebuild() {
        forceRebuild = true;
        engineDirty = true;
    }

    /** Rebuild if a rebuild was requested since the last flush (tick-end hook). */
    public static void flushEngineRebuildIfDirty() {
        if (!engineDirty) return;
        engineDirty = false;
        List<RecipeHolder<?>> known = knownEntries();
        int fingerprint = knownFingerprint(known);
        if (fingerprint == lastRebuildFingerprint && !forceRebuild) return;
        forceRebuild = false;
        lastRebuildFingerprint = fingerprint;
        rebuildEngineInternal(known);
    }

    /** Cheap identity hash of the known ids (insertion order), so unchanged
     *  rebuildCollections storms are detected in O(n). */
    private static int knownFingerprint(List<RecipeHolder<?>> known) {
        int hash = 0;
        for (RecipeHolder<?> entry : known) {
            hash = hash * 31 + entry.id().hashCode();
        }
        return hash;
    }

    /** Sync rebuild (recipe book setupCollections path / forced open fallback). */
    public static synchronized void rebuildEngine() {
        engineDirty = false;
        lastRebuildFingerprint = knownFingerprint(knownEntries());
        rebuildEngineInternal(knownEntries());
    }

    // ── known 集（1.21.1：RecipeBook.known Set<ResourceLocation> → RecipeManager 解析） ──

    /** The recipe book's known recipe holders (unlocked subset), or empty. */
    public static List<RecipeHolder<?>> knownEntries() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return List.of();
        RecipeManager manager = mc.level.getRecipeManager();
        Set<ResourceLocation> known =
                ((RecipeBookAccessor) (net.minecraft.stats.RecipeBook) mc.player.getRecipeBook())
                        .brbe$getKnown();
        if (known == null || known.isEmpty()) return List.of();
        List<RecipeHolder<?>> out = new ArrayList<>(known.size());
        for (ResourceLocation id : known) {
            manager.byKey(id).ifPresent(out::add);
        }
        return out;
    }

    // ── 工作站注册表（1.21.11 Workstation record 移植，Identifier→ResourceLocation） ──

    public enum Family { CRAFTING, FURNACE, STONECUTTING, SMITHING,
                          ANVIL, BREWING, GRINDSTONE, COMPOSTING }

    /** A workstation: type id, recipe-book category-path prefixes it serves,
     *  its block items and whether it has a recipe-book UI. */
    private record Workstation(Family family, String typeId,
                               List<String> categoryPrefixes,
                               List<ResourceLocation> stationItems,
                               boolean recipeBook) {

        boolean matchesPath(String path) {
            for (String prefix : categoryPrefixes) {
                boolean match = prefix.endsWith("_")
                        // prefix like "furnace_" matches both a bare "furnace"
                        // (1.21.1 categoryPath from RecipeType) and the
                        // recipe-book subpaths like "furnace_food" expected in
                        // 1.21.11 — otherwise crafting/furnace never register.
                        ? (path.equals(prefix.substring(0, prefix.length() - 1))
                                || path.startsWith(prefix))
                        : path.equals(prefix);
                if (match) return true;
            }
            return false;
        }

        boolean hasItem(Item item) {
            return stationItems.contains(BuiltInRegistries.ITEM.getKey(item));
        }

        ItemStack[] fallbackIcons() {
            List<ItemStack> icons = new ArrayList<>();
            for (ResourceLocation id : stationItems) {
                BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> icons.add(new ItemStack(item)));
            }
            return icons.toArray(new ItemStack[0]);
        }
    }

    /** Built-in workstation registry (1.21.11 same table; recipeBook=false for
     *  stonecutter/anvil/brewing/grindstone/composter). */
    private static final List<Workstation> BUILTIN_WORKSTATIONS = List.of(
            new Workstation(Family.CRAFTING, "minecraft:crafting", List.of("crafting_"),
                    List.of(ResourceLocation.withDefaultNamespace("crafting_table"),
                            ResourceLocation.withDefaultNamespace("crafter")), true),
            new Workstation(Family.FURNACE, "minecraft:smelting", List.of("furnace_"),
                    List.of(ResourceLocation.withDefaultNamespace("furnace")), true),
            new Workstation(Family.FURNACE, "minecraft:blasting", List.of("blast_furnace_"),
                    List.of(ResourceLocation.withDefaultNamespace("blast_furnace")), true),
            new Workstation(Family.FURNACE, "minecraft:smoking", List.of("smoker_"),
                    List.of(ResourceLocation.withDefaultNamespace("smoker")), true),
            new Workstation(Family.FURNACE, "minecraft:campfire_cooking", List.of("campfire"),
                    List.of(ResourceLocation.withDefaultNamespace("campfire"),
                            ResourceLocation.withDefaultNamespace("soul_campfire")), true),
            new Workstation(Family.STONECUTTING, "minecraft:stonecutting", List.of("stonecutter"),
                    List.of(ResourceLocation.withDefaultNamespace("stonecutter")), false),
            new Workstation(Family.SMITHING, "minecraft:smithing", List.of("smithing"),
                    List.of(ResourceLocation.withDefaultNamespace("smithing_table")), true),
            new Workstation(Family.ANVIL, "minecraft:anvil", List.of("anvil"),
                    List.of(ResourceLocation.withDefaultNamespace("anvil"),
                            ResourceLocation.withDefaultNamespace("chipped_anvil"),
                            ResourceLocation.withDefaultNamespace("damaged_anvil")), false),
            new Workstation(Family.BREWING, "minecraft:brewing", List.of("brewing"),
                    List.of(ResourceLocation.withDefaultNamespace("brewing_stand")), false),
            new Workstation(Family.GRINDSTONE, "minecraft:grindstone", List.of("grindstone"),
                    List.of(ResourceLocation.withDefaultNamespace("grindstone")), false),
            new Workstation(Family.COMPOSTING, "minecraft:compostable", List.of("compost"),
                    List.of(ResourceLocation.withDefaultNamespace("composter")), false));

    private static final List<WorkstationSpec> EXTERNAL_SPECS = new ArrayList<>();
    private static volatile List<Workstation> WORKSTATIONS;

    /** Json DTO for {@code config/brbe_workstations.json}. */
    private static final class WorkstationFile {
        List<StationEntry> workstations;
    }

    private static final class StationEntry {
        String family;
        String typeId;
        List<String> categoryPrefixes;
        List<String> items;
    }

    public record WorkstationSpec(String family, String typeId, List<String> categoryPrefixes,
                                  List<String> items) {}

    /** Effective registry: built-ins + config file + external specs. */
    private static List<Workstation> workstations() {
        List<Workstation> cached = WORKSTATIONS;
        if (cached == null) {
            synchronized (RecipeViewerIndex.class) {
                cached = WORKSTATIONS;
                if (cached == null) {
                    cached = buildWorkstations();
                    WORKSTATIONS = cached;
                }
            }
        }
        // hide 开关源级过滤：无配方书站（config/external 全为 false + 内建切石机/铁砧/酿造/研磨/堆肥）
        // 从整个查询系统前置移除（1.21.11 语义）。
        if (BetterRecipeBook.config.hideNoRecipeBookStationObjects) {
            List<Workstation> filtered = new ArrayList<>();
            for (Workstation station : cached) {
                if (station.recipeBook()) filtered.add(station);
            }
            return filtered;
        }
        return cached;
    }

    private static List<Workstation> buildWorkstations() {
        List<Workstation> out = new ArrayList<>(BUILTIN_WORKSTATIONS);
        out.addAll(loadConfigWorkstations());
        out.addAll(loadExternalWorkstations());
        return List.copyOf(out);
    }

    private static List<Workstation> loadConfigWorkstations() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.gameDirectory == null) return List.of();
            Path file = mc.gameDirectory.toPath().resolve("config").resolve("brbe_workstations.json");
            if (!Files.exists(file)) return List.of();
            String json = Files.readString(file, StandardCharsets.UTF_8);
            WorkstationFile parsed = new Gson().fromJson(json, WorkstationFile.class);
            if (parsed == null || parsed.workstations == null) return List.of();
            List<Workstation> out = new ArrayList<>();
            for (StationEntry entry : parsed.workstations) {
                Workstation station = parseStationEntry(entry);
                if (station != null) out.add(station);
            }
            return out;
        } catch (Exception e) {
            BetterRecipeBook.LOGGER.warn("[BRBE] failed to load brbe_workstations.json: {}", e.toString());
            return List.of();
        }
    }

    private static Workstation parseStationEntry(StationEntry entry) {
        return parseWorkstation(entry.family, entry.typeId, entry.categoryPrefixes, entry.items, false);
    }

    private static Workstation parseWorkstation(String familyName, String typeId,
                                                List<String> prefixes, List<String> items,
                                                boolean recipeBook) {
        try {
            Family family = Family.valueOf(familyName.toUpperCase());
            if (typeId == null || typeId.isBlank()) return null;
            if (prefixes == null || prefixes.isEmpty() || items == null || items.isEmpty()) return null;
            List<ResourceLocation> stations = new ArrayList<>();
            for (String item : items) {
                String[] parts = item.split(":", 2);
                ResourceLocation id = parts.length == 2
                        ? ResourceLocation.fromNamespaceAndPath(parts[0], parts[1])
                        : ResourceLocation.withDefaultNamespace(parts[0]);
                if (id != null) stations.add(id);
            }
            if (stations.isEmpty()) return null;
            return new Workstation(family, typeId, List.copyOf(prefixes), List.copyOf(stations), recipeBook);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Companion plugins register external stations (same typeId replaces). */
    public static void registerExternalWorkstations(List<WorkstationSpec> specs) {
        if (specs == null || specs.isEmpty()) return;
        synchronized (RecipeViewerIndex.class) {
            for (WorkstationSpec spec : specs) {
                if (spec == null || spec.typeId() == null) continue;
                EXTERNAL_SPECS.removeIf(s -> spec.typeId().equals(s.typeId()));
                EXTERNAL_SPECS.add(spec);
            }
            if (WORKSTATIONS != null) WORKSTATIONS = null;
        }
    }

    private static List<Workstation> loadExternalWorkstations() {
        List<Workstation> out = new ArrayList<>();
        synchronized (RecipeViewerIndex.class) {
            for (WorkstationSpec spec : EXTERNAL_SPECS) {
                Workstation station = parseWorkstation(spec.family(), spec.typeId(),
                        spec.categoryPrefixes(), spec.items(), externalHasRecipeBook(spec.typeId()));
                if (station != null) out.add(station);
            }
        }
        return out;
    }

    /** AUTHORITY signal for hide: mod station types with a recipe-book UI. */
    private static boolean externalHasRecipeBook(String typeId) {
        return switch (typeId) {
            case "minecraft:smelting", "minecraft:smoking", "minecraft:campfire_cooking",
                 "minecraft:stonecutting", "minecraft:smithing", "minecraft:crafting" -> true;
            default -> false;
        };
    }

    // ── 引擎重建（known 集 → Workstation 匹配 → registerType） ───────────────

    /** 1.21.1 的 RecipeType → 配方书分类路径（1.21.11 display.category 的等价物）。 */
    private static String categoryPath(RecipeHolder<?> holder) {
        RecipeType<?> type = holder.value().getType();
        if (type == RecipeType.CRAFTING) return "crafting";
        if (type == RecipeType.SMELTING) return "furnace";
        if (type == RecipeType.BLASTING) return "blast_furnace";
        if (type == RecipeType.SMOKING) return "smoker";
        if (type == RecipeType.CAMPFIRE_COOKING) return "campfire";
        if (type == RecipeType.STONECUTTING) return "stonecutter";
        if (type == RecipeType.SMITHING) return "smithing";
        return "";
    }

    private static void rebuildEngineInternal(List<RecipeHolder<?>> knownEntries) {
        RecipeViewerEngine.clearVanilla();
        Map<String, List<RecipeViewerEngine.IndexedRecipe>> grouped = new LinkedHashMap<>();
        Map<String, List<ItemStack>> stationItems = new LinkedHashMap<>();
        for (Workstation station : workstations()) {
            stationItems.computeIfAbsent(station.typeId(), k -> new ArrayList<>())
                    .addAll(java.util.Arrays.asList(station.fallbackIcons()));
        }
        for (RecipeHolder<?> holder : knownEntries) {
            String path = categoryPath(holder);
            boolean matched = false;
            for (Workstation station : workstations()) {
                if (!station.matchesPath(path)) continue;
                grouped.computeIfAbsent(station.typeId(), k -> new ArrayList<>())
                        .add(toIndexed(holder, Minecraft.getInstance().level.registryAccess()));
                matched = true;
                break;
            }
            if (!matched && !path.isEmpty()) {
                // 1.21.1 无已知路径命中（切石/锻造等只走 JEI 通道）——跳过
            }
        }
        // 切石/锻造也走 known 集注册（1.21.1 原版有 STONECUTTING/SMITHING
        // RecipeType，categoryPath/工作站均匹配）。此前在此 skip —— 但
        // headless-jei 1.21.1 的 SKIP_VANILLA 同样排除这两类 → 两边都不注册
        // → 切石/锻造 tab 恒空。故交由 known 集路径注册（重复注册由
        // registerType 同 uid 覆盖，幂等）。
        for (Map.Entry<String, List<RecipeViewerEngine.IndexedRecipe>> e : grouped.entrySet()) {
            RecipeViewerEngine.registerType(e.getKey(), e.getValue(), stationItems.get(e.getKey()));
        }
        // hide 源级：无配方书站（切石机/铁砧/酿造/研磨/堆肥）在 workstations() 已剔除，
        // 但其 JEI 通道数据仍在——按隐藏类别清掉（否则 defaultFor 绕过过滤打开类别）。
        if (BetterRecipeBook.config.hideNoRecipeBookStationObjects) {
            for (String uid : List.of("minecraft:anvil", "minecraft:brewing",
                    "minecraft:grindstone", "minecraft:compostable")) {
                RecipeViewerEngine.clearType(uid);
            }
        }
        // 配方书工作站物品集（engine 权威判定用）
        RecipeViewerEngine.setRecipeBookStationItems(vanillaWorkstationItems());
        BetterRecipeBook.LOGGER.info("[BRBE] rebuildEngine known={} types={}",
                knownEntries.size(), grouped.size());
        RecipeViewerEngine.notifyRebuiltPublic();
    }

    private static RecipeViewerEngine.IndexedRecipe toIndexed(RecipeHolder<?> holder,
                                                               net.minecraft.core.HolderLookup.Provider registryAccess) {
        Recipe<?> recipe = holder.value();
        List<ItemStack> inputs = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            ItemStack[] stacks = ingredient.getItems();
            if (stacks.length > 0) inputs.add(stacks[0]);
        }
        ItemStack result = recipe.getResultItem(registryAccess);
        List<ItemStack> outputs = result.isEmpty() ? List.of() : List.of(result);
        return new RecipeViewerEngine.IndexedRecipe(holder, inputs, outputs);
    }

    /** Only recipeBook=true stations' items (mod stations on vanilla types
     *  don't count either). */
    public static List<ItemStack> vanillaWorkstationItems() {
        List<ItemStack> out = new ArrayList<>();
        for (Workstation station : workstations()) {
            if (station.recipeBook()) {
                out.addAll(java.util.Arrays.asList(station.fallbackIcons()));
            }
        }
        return out;
    }

    /** All workstation items of a family (registry order). */
    public static List<ItemStack> workstationItems(Family family) {
        List<ItemStack> out = new ArrayList<>();
        for (Workstation station : workstations()) {
            if (station.family == family) {
                out.addAll(java.util.Arrays.asList(station.fallbackIcons()));
            }
        }
        return out;
    }

    /** 工作站列（FURNACE family 分组自底向上：烧炼→熔炼→烟熏→营火）。 */
    public static List<ItemStack> furnaceStationColumnItems() {
        List<ItemStack> out = new ArrayList<>();
        Set<Item> seen = new HashSet<>();
        for (String prefix : FURNACE_SUBCATEGORY_PREFIXES) {
            for (Workstation station : workstations()) {
                if (!station.matchesPath(prefix)) continue;
                for (ItemStack icon : station.fallbackIcons()) {
                    if (seen.add(icon.getItem())) out.add(icon);
                }
            }
        }
        return out;
    }

    private static final List<String> FURNACE_SUBCATEGORY_PREFIXES =
            List.of("furnace_", "blast_furnace_", "smoker_", "campfire");

    /** 查询浮层左侧工作站列（按类别 id；1.21.1 兼容旧调用——新 UI 用 workstationItems/furnaceStationColumnItems）。 */
    public static List<ItemStack> stationColumnItemsFor(String categoryId) {
        if (categoryId == null) return List.of();
        Family family = switch (categoryId) {
            case "crafting" -> Family.CRAFTING;
            case "furnace", "fuel" -> Family.FURNACE;
            case "stonecutting" -> Family.STONECUTTING;
            case "smithing" -> Family.SMITHING;
            case "anvil" -> Family.ANVIL;
            case "brewing" -> Family.BREWING;
            case "grindstone" -> Family.GRINDSTONE;
            case "compost" -> Family.COMPOSTING;
            default -> null;
        };
        if (family == null) return List.of();
        return family == Family.FURNACE ? furnaceStationColumnItems() : workstationItems(family);
    }

    /** 某类别前缀（如 "furnace_"）对应工作站块的物品图标（1.21.11
     *  workstationsIconsForPrefix 等价物）——熔炉 tooltip 按站别行 + 图标用。 */
    public static List<ItemStack> workstationsIconsForPrefix(String categoryPrefix) {
        if (categoryPrefix == null) return List.of();
        List<ItemStack> icons = new ArrayList<>();
        for (Workstation station : workstations()) {
            if (!station.matchesPath(categoryPrefix)) continue;
            for (ItemStack icon : station.fallbackIcons()) {
                icons.add(icon);
            }
        }
        return icons;
    }

    /** 熔炉类别的四站烧炼耗时 {furnace, blast, smoker, campfire}（1.21.11
     *  furnaceStationTicks 等价物：同内容配方按站别取 cookingTime）。 */
    public static int[] furnaceStationTicks(RecipeHolder<?> sample) {
        int[] ticks = new int[4];
        if (sample == null) return ticks;
        String key = furnaceContentKey(sample);
        if (key == null) return ticks;
        for (RecipeHolder<?> holder : knownEntries()) {
            if (!(holder.value() instanceof AbstractCookingRecipe cooking)) continue;
            String k = furnaceContentKey(holder);
            if (k == null || !k.equals(key)) continue;
            int idx;
            if (cooking.getType() == RecipeType.SMELTING) idx = 0;
            else if (cooking.getType() == RecipeType.BLASTING) idx = 1;
            else if (cooking.getType() == RecipeType.SMOKING) idx = 2;
            else if (cooking.getType() == RecipeType.CAMPFIRE_COOKING) idx = 3;
            else idx = -1;
            if (idx >= 0) {
                ticks[idx] = cooking.getCookingTime();
            }
        }
        return ticks;
    }

    /** 同内容配方去重键（排序原料 ids + "->" + 排序结果 ids）。 */
    public static String furnaceContentKey(RecipeHolder<?> holder) {
        try {
            if (!(holder.value() instanceof AbstractCookingRecipe cooking)) return null;
            List<String> in = new ArrayList<>();
            for (Ingredient ingredient : cooking.getIngredients()) {
                for (ItemStack stack : ingredient.getItems()) {
                    in.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                    break;
                }
            }
            List<String> out = new ArrayList<>();
            ItemStack result = cooking.getResultItem(Minecraft.getInstance().level.registryAccess());
            if (!result.isEmpty()) out.add(BuiltInRegistries.ITEM.getKey(result.getItem()).toString());
            Collections.sort(in);
            Collections.sort(out);
            return String.join("+", in) + "->" + String.join("+", out);
        } catch (Exception e) {
            return null;
        }
    }

    // ── 燃料辅助（1.21.1 AbstractFurnaceBlockEntity 体系，原样保留） ──────────

    public static boolean isFuelItem(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.isFuel(stack);
    }

    public static List<ItemStack> allFuelItems() {
        List<ItemStack> fuels = new ArrayList<>();
        for (var entry : net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.getFuel().entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                fuels.add(new ItemStack(entry.getKey()));
            }
        }
        return fuels;
    }

    public static int burnDuration(ItemStack fuel) {
        return (fuel == null || fuel.isEmpty()) ? 0
                : net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.getFuel()
                        .getOrDefault(fuel.getItem(), 0);
    }

    // ── viewer 集合 / partial 快照（1.21.11 RecipeViewerIndex 移植） ─────────

    private static final Set<RecipeCollection> viewerCollections =
            Collections.newSetFromMap(new WeakHashMap<>());

    public static RecipeCollection toCollection(List<RecipeHolder<?>> holders,
                                                StackedContents stackedContents,
                                                net.minecraft.stats.RecipeBook book) {
        Minecraft mc = Minecraft.getInstance();
        RecipeCollection collection = new RecipeCollection(mc.level.registryAccess(), holders);
        collection.updateKnownRecipes(book);
        collection.canCraft(stackedContents, 2, 2, book);
        viewerCollections.add(collection);
        return collection;
    }

    public static boolean isViewerCollection(RecipeCollection collection) {
        return collection != null && viewerCollections.contains(collection);
    }

    /** viewer partial 快照：独立于 PartialCraftingUtil 的代数 tagger
     *  （其代数随配方书 updateCollections 推进会作废 viewer 标记）。 */
    private static final Map<RecipeCollection, Set<ResourceLocation>> viewerPartials =
            new IdentityHashMap<>();

    public static void snapshotPartials(RecipeCollection collection) {
        if (collection == null) return;
        Set<ResourceLocation> ids = new HashSet<>();
        for (RecipeHolder<?> holder : collection.getRecipes()) {
            if (com.alonie.brbe.util.PartialCraftingUtil.isPartiallyCraftableEvenIfStale(
                    collection, holder.id())) {
                ids.add(holder.id());
            }
        }
        viewerPartials.put(collection, ids);
    }

    public static boolean isViewerPartial(RecipeCollection collection, ResourceLocation recipeId) {
        Set<ResourceLocation> ids = viewerPartials.get(collection);
        return ids != null && ids.contains(recipeId);
    }

    public static void clearViewerPartials(RecipeCollection collection) {
        if (collection != null) viewerPartials.remove(collection);
    }

    // ── viewer 激活状态（mixin 守卫用） ─────────────────────────────────────

    private static volatile boolean viewerActive;
    private static volatile boolean viewerOpenedFromBook;

    public static void setViewerActive(boolean active) {
        viewerActive = active;
        if (!active) viewerOpenedFromBook = false;
    }

    public static boolean isViewerActive() {
        return viewerActive;
    }

    public static void setViewerOpenedFromBook(boolean value) {
        viewerOpenedFromBook = value;
    }

    public static boolean isViewerOpenedFromBook() {
        return viewerOpenedFromBook;
    }
}

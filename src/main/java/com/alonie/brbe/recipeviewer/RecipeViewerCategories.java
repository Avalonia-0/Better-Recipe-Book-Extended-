package com.alonie.brbe.recipeviewer;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.recipeviewer.engine.RecipeViewerEngine;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry of viewer categories.  Add new categories (furnace, smithing, …)
 * here and they automatically appear as bottom tabs on the overlay.  The
 * companion {@code brbe-jei-plugins} mod appends dynamic categories for each
 * mod recipe type via {@link #registerExternal}.
 */
public final class RecipeViewerCategories {

    private RecipeViewerCategories() {}

    /** Built-in categories (vanilla recipe types). */
    private static final List<RecipeViewerCategory> BUILTIN =
            List.of(new FurnaceRecipeCategory(), new CraftingRecipeCategory(),
                    new FuelRecipeCategory(), new StonecuttingRecipeCategory(),
                    new SmithingRecipeCategory(), new AnvilRecipeCategory(),
                    new BrewingRecipeCategory(), new GrindstoneRecipeCategory(),
                    new CompostRecipeCategory(), new InfoRecipeCategory());

    /** Categories appended by the companion mod (mod recipe types). */
    private static final List<RecipeViewerCategory> EXTERNAL = new CopyOnWriteArrayList<>();

    private static volatile List<RecipeViewerCategory> ALL;

    /** Set by the JEI plugin collector after each re-collection: the
     *  "category has no visible objects" computation (category-tab hiding) is
     *  stale until this flag is consumed by the overlay. */
    private static volatile boolean visibilityDirty = true;

    /** Mark the category-visibility computation stale (called after every
     *  plugin re-collection). */
    public static void markVisibilityDirty() {
        visibilityDirty = true;
    }

    /** Whether the visibility computation is stale; consuming resets it. */
    public static boolean consumeVisibilityDirty() {
        boolean dirty = visibilityDirty;
        visibilityDirty = false;
        return dirty;
    }

    /** All categories: built-in followed by externally registered ones. */
    public static List<RecipeViewerCategory> all() {
        List<RecipeViewerCategory> cached = ALL;
        if (cached == null) {
            cached = buildAll();
            ALL = cached;
        }
        return cached;
    }

    /** Registers categories collected from mod JEI plugins.  Idempotent. */
    public static void registerExternal(List<RecipeViewerCategory> categories) {
        if (categories == null || categories.isEmpty()) return;
        boolean changed = false;
        for (RecipeViewerCategory category : categories) {
            if (category != null && !EXTERNAL.contains(category)) {
                EXTERNAL.add(category);
                changed = true;
            }
        }
        if (changed) {
            ALL = null;
            jeiPositionsLoaded = false; // 新类别加入 → 全量 uid 集合变化，重算顺序
        }
    }

    private static List<RecipeViewerCategory> buildAll() {
        List<RecipeViewerCategory> out = new ArrayList<>(BUILTIN);
        out.addAll(EXTERNAL);
        // JEI 同款类别排序：标签顺序 = 各类别 JEI 类型 uid 在 JEI 类别顺序中的
        // 最小位置（多 uid 类别如炉族聚合按其成员最先出现的槽位）。
        Map<String, Integer> positions = jeiPositions();
        out.sort(java.util.Comparator.comparingInt(c -> jeiPosition(c, positions)));
        return List.copyOf(out);
    }

    /** Category's sort position in JEI's category order (min over its uids). */
    private static int jeiPosition(RecipeViewerCategory category, Map<String, Integer> positions) {
        int best = Integer.MAX_VALUE;
        for (String uid : category.jeiTypeUids()) {
            Integer p = positions.get(uid);
            if (p != null && p < best) best = p;
        }
        return best;
    }

    /** JEI 类别顺序（uid → 位置）。来源：JEI 的
     *  {@code config/jei/recipe-category-sort-order.ini} —— vendored fork 的
     *  {@code RecipeCategorySortingConfig}（起始为默认规则计算、随后持久化、
     *  用户可改），BRBE 直接采用即与 JEI 所见顺序一致；配置文件缺席时按
     *  JEI 默认规则在全量 uid 并集上计算。 */
    private static volatile Map<String, Integer> JEI_POSITIONS;
    private static volatile boolean jeiPositionsLoaded;

    private static Map<String, Integer> jeiPositions() {
        if (!jeiPositionsLoaded) {
            synchronized (RecipeViewerCategories.class) {
                if (!jeiPositionsLoaded) {
                    JEI_POSITIONS = loadJeiPositions();
                    jeiPositionsLoaded = true;
                }
            }
        }
        return JEI_POSITIONS;
    }

    private static Map<String, Integer> loadJeiPositions() {
        List<String> iniOrder = null;
        try {
            java.nio.file.Path ini = net.fabricmc.loader.api.FabricLoader.getInstance()
                    .getConfigDir().resolve("jei").resolve("recipe-category-sort-order.ini");
            if (java.nio.file.Files.exists(ini)) {
                List<String> parsed = java.nio.file.Files.readAllLines(ini).stream()
                        .map(String::trim).filter(s -> !s.isEmpty()).toList();
                if (!parsed.isEmpty()) iniOrder = parsed;
            }
        } catch (Exception | LinkageError ignored) {
        }
        java.util.Set<String> allUids = new java.util.LinkedHashSet<>();
        for (RecipeViewerCategory c : BUILTIN) allUids.addAll(c.jeiTypeUids());
        for (RecipeViewerCategory c : EXTERNAL) allUids.addAll(c.jeiTypeUids());
        Map<String, Integer> positions = new java.util.HashMap<>();
        int idx = 0;
        if (iniOrder != null) {
            for (String uid : iniOrder) positions.put(uid, idx++);
        }
        // 未在 ini 中的 uid（无配置文件 / 新注册类别）按 JEI 默认规则排在已知之后
        List<String> rest = new ArrayList<>();
        for (String uid : allUids) {
            if (!positions.containsKey(uid)) rest.add(uid);
        }
        for (String uid : jeiDefaultOrder(rest)) positions.put(uid, idx++);
        return positions;
    }

    /** JEI 的 {@code RecipeCategorySortingConfig.getDefaultSortOrder}：
     *  minecraft:crafting 置顶 → minecraft:* 优先 → 完整 uid 字母序。 */
    private static List<String> jeiDefaultOrder(List<String> uids) {
        java.util.Comparator<String> c = java.util.Comparator
                .comparing((String s) -> s.equals("minecraft:crafting")).reversed()
                .thenComparing(java.util.Comparator
                        .comparing((String s) -> s.startsWith("minecraft:")).reversed())
                .thenComparing(java.util.Comparator.naturalOrder());
        return uids.stream().sorted(c).toList();
    }

    /**
     * Pick the default category for {@code target} on open.  When the query is
     * triggered from inside a workstation container (the open screen's menu is
     * a workstation — crafting table / furnace / smithing table / anvil /
     * brewing stand / stonecutter / grindstone), jump to the category whose
     * workstation column contains that workstation.  Only the inventory
     * screen's 2x2 grid is excluded (it is not a workstation, despite sharing
     * the crafting menu base class); no category contains the workstation =>
     * fall back to the first (leftmost) category.  This menu-aware jump runs
     * only on the opening query turn (browse-all / tab clicks don't go through
     * here), and only when the open menu IS a workstation — the player is
     * physically at a station.
     *
     * <p>Otherwise (not inside a workstation container): a workstation block's
     * usage view wins first (JEI semantics); otherwise the applicable category
     * with the highest {@link RecipeViewerCategory#defaultPriority} whose query
     * yields at least one entry.  Returns null when no category can show
     * anything for {@code target} (the viewer does not open).
     */
    public static RecipeViewerCategory defaultFor(ItemStack target, boolean usage,
                                                  AbstractContainerMenu menu) {
        // [DEBUG-bug1] Temporary: capture the actual default-category decision
        // (menu / usage / station-usage / workstation-menu / bestByPriority) so
        // the "workstation not in any category always opens second category"
        // report can be diagnosed from a real run.
        boolean dbgIsWsm = isWorkstationMenu(menu);
        RecipeViewerCategory dbgStation = usage ? stationUsageCategory(target) : null;
        RecipeViewerCategory dbgWsCat = dbgIsWsm ? stationCategoryFor(menu) : null;
        BetterRecipeBook.LOGGER.warn("[DEBUG-bug1] defaultFor target={} usage={} menu={} isWsm={} stationCat={} wsCat={} first={}",
                target == null ? "null" : target.getItem(), usage,
                menu == null ? "null" : menu.getClass().getSimpleName(), dbgIsWsm,
                dbgStation == null ? "null" : dbgStation.id(),
                dbgWsCat == null ? "null" : dbgWsCat.id(),
                all().isEmpty() ? "null" : all().get(0).id());
        // 1) Usage query of a workstation block: THAT station's category wins
        //    (JEI semantics), regardless of which container the player is in.
        //    Must run BEFORE the container menu-jump below — a U-query on a
        //    workstation block (e.g. the smithing table) performed while inside
        //    another workstation-owned container (e.g. crafting table) must open
        //    the QUERIED station's category, not the container's.
        if (usage) {
            RecipeViewerCategory stationCategory = stationUsageCategory(target);
            if (stationCategory != null && isProgressCategory(stationCategory)) {
                return stationCategory;
            }
        }
        // 2) Otherwise, inside a workstation container: jump to its category.
        //    (Only on the opening query turn — switchCategory / browse-all
        //    never call defaultFor.)
        if (isWorkstationMenu(menu)) {
            RecipeViewerCategory workstationCategory = stationCategoryFor(menu);
            if (workstationCategory != null && isProgressCategory(workstationCategory)) {
                return workstationCategory;
            }
            RecipeViewerCategory first = firstProgressCategory();
            return first != null ? first : all().get(0);
        }
        // 3) Smart default by priority.
        return bestByPriority(target, usage);
    }

    /** Whether the target is itself a registered workstation: its usage query
     *  routes to a station category ({@link #stationUsageCategory}) — not
     *  merely through the container-menu jump.  The workstation-title trigger
     *  accepts a crosshair block only when this holds: an unregistered
     *  vanilla-variant station (e.g. a Better End end-stone smelter sharing the
     *  furnace GUI) must fall back to the MENU family instead — otherwise its
     *  query would target an unregistered block and open an empty category. */
    public static boolean isRegisteredStationTarget(ItemStack target) {
        return target != null && !target.isEmpty() && stationUsageCategory(target) != null;
    }

    /** The category for a usage query of a workstation block: the first station
     *  category that {@link RecipeViewerCategory#appliesToStation applies} with
     *  content (grid categories exempt from the hide toggle), falling back to
     *  the first matching station category — or null when the target is not a
     *  workstation block the query reacts to. */
    private static RecipeViewerCategory stationUsageCategory(ItemStack target) {
        RecipeViewerCategory firstMatch = null;
        for (RecipeViewerCategory category : all()) {
            if (!category.appliesToStation(target)) continue;
            // 进度模式（hideNoRecipeBookStationObjects）：信息行类别（燃料/
            // 堆肥/信息）整体隐藏，无配方书体系的工作站类别（切石/铁砧/研磨）
            // 隐藏，非法工作站与类别的连接切断——只显示和进度相关的对象。
            if (BetterRecipeBook.config.hideNoRecipeBookStationObjects
                    && (!isProgressCategory(category)
                        || (!category.isGridCategory()
                            && !RecipeViewerEngine.isRecipeBookStation(target)))) {
                continue;
            }
            // A workstation may serve several categories.  Prefer the first
            // match that actually has content, falling back to the first match
            // when none does — otherwise the query lands on an empty vanilla
            // type (unlock-all off) instead of the mod type's recipes.
            if (category.hasContent(target, true)) {
                return category;
            }
            if (firstMatch == null) {
                firstMatch = category;
            }
        }
        if (firstMatch != null) {
            // The station categories have no content (e.g. the smithing table
            // with no unlocked recipes).  Before settling on the empty default
            // — which drops the query to the external viewer — prefer any
            // category that CAN show this query (the fuel tab for a burnable
            // workstation).  bestByPriority already checks hasContent.
            RecipeViewerCategory alternative = bestByPriority(target, true);
            if (alternative != null) {
                return alternative;
            }
            return firstMatch;
        }
        return null;
    }

    /** Whether {@code menu} is a workstation container the query reacts to:
     *  crafting table, furnace-family, smithing table, anvil, brewing stand,
     *  stonecutter or grindstone.  The player's own inventory (2×2 crafting
     *  grid) shares the crafting menu base class but is NOT a workstation — it
     *  must not hijack the query into the crafting category. */
    private static boolean isWorkstationMenu(AbstractContainerMenu menu) {
        if (menu == null || menu instanceof InventoryMenu) return false;
        for (RecipeViewerCategory category : all()) {
            if (category.appliesToMenu(menu)) return true;
        }
        return false;
    }

    /** The category whose workstation column contains the open workstation
     *  menu, or null.  A workstation may serve several categories; the first
     *  match in tab order wins (leftmost). */
    private static RecipeViewerCategory stationCategoryFor(AbstractContainerMenu menu) {
        if (menu == null || menu instanceof InventoryMenu) return null;
        for (RecipeViewerCategory category : all()) {
            if (category.appliesToMenu(menu)) return category;
        }
        return null;
    }

    /** The applicable category with the highest {@link RecipeViewerCategory
     *  #defaultPriority} whose query yields at least one entry, or null.
     *  进度模式下跳过非进度类别（信息行/无配方书工作站类别）。 */
    private static RecipeViewerCategory bestByPriority(ItemStack target, boolean usage) {
        RecipeViewerCategory best = null;
        int bestPriority = -1;
        for (RecipeViewerCategory category : all()) {
            if (!isProgressCategory(category)) continue;
            int priority = category.defaultPriority(target);
            if (priority <= bestPriority) continue;
            if (category.hasContent(target, usage)) {
                best = category;
                bestPriority = priority;
            }
        }
        return best;
    }

    /** 进度模式（hideNoRecipeBookStationObjects）下类别是否可见：信息行类别
     *  （燃料/堆肥/信息——非进度对象，开启后隐藏）与无配方书体系的工作站类别
     *  （切石/铁砧/研磨）不可见；配方书体系的内置类别（合成/烧炼/锻造/酿造——
     *  BRBE 自带酿造配方书）与配方书驱动的 mod 类别可见。开关关闭时恒为 true。 */
    public static boolean isProgressCategory(RecipeViewerCategory category) {
        if (!BetterRecipeBook.config.hideNoRecipeBookStationObjects) return true;
        if (category == null || category.isGridCategory()) return false;
        List<String> uids = category.jeiTypeUids();
        if (uids.isEmpty()) return false;
        for (String uid : uids) {
            if (!RecipeViewerEngine.isProgressType(uid)) return false;
        }
        return true;
    }

    /** 进度模式下第一个可见类别（标签顺序），无则 null。 */
    private static RecipeViewerCategory firstProgressCategory() {
        for (RecipeViewerCategory category : all()) {
            if (isProgressCategory(category)) return category;
        }
        return null;
    }
}

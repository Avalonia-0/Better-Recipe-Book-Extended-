package com.alonie.brbe.cache;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.equipment.trim.TrimPattern;

import java.util.*;
import java.util.stream.Collectors;
import com.alonie.brbe.util.BrbeLogger;

/**
 * Local vanilla recipe cache that supplements server-provided recipes.
 *
 * Recipes are loaded from the classpath (Minecraft JAR) at startup by
 * {@link VanillaRecipeLoader} — no file I/O, no singleplayer capture needed.
 *
 * <h3>Dual-mode injection</h3>
 * <ul>
 *   <li><b>No server recipes</b> (known has zero server entries): inject ALL
 *       valid cached entries.  Covers servers that never send recipe packets
 *       (e.g. Hypixel).</li>
 *   <li><b>Server recipes present</b>: complement mode — only inject cache
 *       entries whose result item is NOT already covered by the server.
 *       Eliminates duplicates in singleplayer while filling gaps on
 *       partial-recipe servers.</li>
 * </ul>
 *
 * <h3>ID-space partition</h3>
 * Cache entries use <b>negative</b> {@link RecipeDisplayId} values (starting
 * at -1, decrementing).  Server-assigned IDs are always non-negative.  This
 * creates a hard partition: {@code isLocalRecipe(id)} is simply
 * {@code id.index() &lt; 0}, with zero collision risk regardless of what IDs
 * the server sends later.
 */
public final class VanillaRecipeCache {

    private static final int SAMPLE_SIZE = 15;

    private static final Map<String, CacheableRecipeDisplayEntry> cache = new LinkedHashMap<>();
    private static final List<String> lastInjected = new ArrayList<>();
    private static final List<String> lastFiltered = new ArrayList<>();
    private static final Map<String, Integer> lastCategoryBreakdown = new LinkedHashMap<>();
    private static int lastServerCount = 0;
    /** 缓存内容的世代号：{@link #init()} 每次重载 +1，供
     *  {@code RecipeNamespaceIndex} 判断是否需要按新内容重建索引。 */
    private static int generation = 0;

    private VanillaRecipeCache() {}

    public static void init() {
        cache.clear();
        generation++;
        List<CacheableRecipeDisplayEntry> loaded = VanillaRecipeLoader.loadAll();
        for (CacheableRecipeDisplayEntry entry : loaded) {
            if (entry != null && entry.recipeKey() != null) {
                cache.put(entry.recipeKey(), entry);
            }
        }
        BrbeLogger.log("BRBE-CACHE", "init loaded {} vanilla recipes from classpath", cache.size());
        Map<String, Long> byCategory = cache.values().stream()
                .collect(Collectors.groupingBy(
                        e -> e.categoryName() != null ? e.categoryName() : "null",
                        LinkedHashMap::new, Collectors.counting()));
        BrbeLogger.log("BRBE-CACHE", "cache by category: {}", byCategory);
    }

    /** **负 id → 配方 key**：注入条目的 display 是本地重建的（不含数据组件等），
     *  与索引里的服务端 display 不相等 → 反查必然 miss（用户 2026-10-02 实测：
     *  `RBIP-DIAG … injected=true recipeId=null`）。数据包档据此按**配方自己的 id**
     *  归属，而不是退回"产物命名空间"（那会让数据包配方流进原版标签）。
     *  每次注入 pass 重建，与 {@code known} 里负 id 的清理同步。 */
    private static final java.util.Map<Integer, String> injectedRecipeKeys = new java.util.HashMap<>();

    /** 注入条目（负 id）对应的配方 key，如 {@code thepa:gun_0}；不是注入条目则 null。 */
    public static String injectedRecipeKey(int idIndex) {
        synchronized (injectedRecipeKeys) {
            return injectedRecipeKeys.get(idIndex);
        }
    }

    public static void clear() {
        BrbeLogger.log("BRBE-CACHE", "session cleared");
        lastInjected.clear();
        lastFiltered.clear();
        synchronized (injectedRecipeKeys) { injectedRecipeKeys.clear(); }
        synchronized (injectedRecipeKeys) { injectedRecipeKeys.clear(); }
        lastCategoryBreakdown.clear();
        lastServerCount = 0;
    }

    public static void detectAndInject(ClientRecipeBook recipeBook,
                                        Map<RecipeDisplayId, RecipeDisplayEntry> known) {
        if (BetterRecipeBook.config != null && !BetterRecipeBook.config.unlockAll) {
            // unlock-all is off: the book must only show server-unlocked recipes.
            // Drop any locally-injected cache entries (negative IDs) so the
            // local complement cannot bypass the toggle.
            known.keySet().removeIf(id -> id.index() < 0);
            lastServerFingerprint = Integer.MIN_VALUE;
            return;
        }
        if (cache.isEmpty()) return;
        // Throttle: rebuildCollections fires repeatedly on item pickup /
        // progression unlocks, and detectAndInject would re-run the full
        // complement pass (server result scan + rebuilding every negative-id
        // entry) on each call.  The negative-id cache entries only depend on
        // the server-known set — when that set is unchanged, the cached
        // entries are already correct, so skip the whole pass.
        int serverFingerprint = serverKnownFingerprint(known);
        if (serverFingerprint == lastServerFingerprint && !forceInject) {
            return;
        }
        lastServerFingerprint = serverFingerprint;
        forceInject = false;
        lastServerCount = (int) known.keySet().stream().filter(id -> id.index() >= 0).count();
        BrbeLogger.log("BRBE-CACHE", "pre-rebuild known count: {} (server: {})",
                known.size(), lastServerCount);
        known.keySet().removeIf(id -> id.index() < 0);
        if (lastServerCount == 0) {
            injectEntries(known, Set.of());
        } else {
            Set<String> serverResultItems = collectServerResultItems(known);
            injectEntries(known, serverResultItems);
        }
    }

    /** Fingerprint of the server-known display ids (non-negative indices),
     *  order-independent (negative-id cache entries are deleted and rebuilt on
     *  each pass, which would otherwise churn the map iteration order and make
     *  an insertion-order hash unstable). */
    private static int serverKnownFingerprint(Map<RecipeDisplayId, RecipeDisplayEntry> known) {
        int hash = 0;
        for (RecipeDisplayId id : known.keySet()) {
            if (id.index() >= 0) {
                hash += id.index() * 31;
            }
        }
        return hash;
    }

    private static int lastServerFingerprint = Integer.MIN_VALUE;
    private static boolean forceInject;

    /** Force the next detectAndInject to run even if the server-known set is
     *  unchanged (used when cache contents or unlock state change). */
    public static void forceNextInject() {
        forceInject = true;
    }

    private static Set<String> collectServerResultItems(Map<RecipeDisplayId, RecipeDisplayEntry> known) {
        Set<String> keys = new HashSet<>();
        // 诊断计数（2026-09-28：纹饰去重键在 1.21.11 上整个失踪过一次，留下"键从哪来"的实据）
        int trimByHolderKey = 0;
        int trimByRegistryLookup = 0;
        int trimByTemplateItem = 0;
        for (RecipeDisplayEntry entry : known.values()) {
            String rid = extractResultItemId(entry.display().result());
            if (rid != null) {
                keys.add(rid);
                continue;
            }
            // Trim recipes have no item result — dedupe by pattern / template item instead.
            String trimId = extractTrimPatternId(entry.display().result());
            if (trimId != null) {
                keys.add("trim:" + trimId);
                if (extractTrimPatternIdFromHolder(entry.display().result()) != null) {
                    trimByHolderKey++;
                } else {
                    trimByRegistryLookup++;
                }
            }
            // 模板物品是**不依赖纹饰图案注册表**的第二把钥匙：holder 反查失败时它仍能去重
            String trimTemplate = extractTrimTemplateItemId(entry.display());
            if (trimTemplate != null) {
                keys.add("trimtmpl:" + trimTemplate);
                trimByTemplateItem++;
            }
        }
        BrbeLogger.log("BRBE-CACHE", "server covers {} unique result items (trim keys: holder={}, registryLookup={}, templateItem={})",
                keys.size(), trimByHolderKey, trimByRegistryLookup, trimByTemplateItem);
        return keys;
    }

    /**
     * Pattern id of a {@link SlotDisplay.SmithingTrimDemoSlotDisplay}, or null.
     *
     * <p>先走 {@link #extractTrimPatternIdFromHolder(SlotDisplay)}（不查注册表），失败再按值在
     * 当前 level 的纹饰图案注册表里反查——后者对 holder 里那个实例与注册表实例的**身份**敏感
     * （{@code Registry.getKey} 走 byValue 身份表），1.21.11 上曾经因此整条去重键失踪
     * （2026-09-28：补全注入仍注入 18 条纹饰条目 → 锻造台纹饰页每组多一份副本）。</p>
     */
    private static String extractTrimPatternId(SlotDisplay slot) {
        String byHolder = extractTrimPatternIdFromHolder(slot);
        if (byHolder != null) {
            return byHolder;
        }
        if (!(slot instanceof SlotDisplay.SmithingTrimDemoSlotDisplay demo)) return null;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return null;
            Registry<TrimPattern> registry =
                    mc.level.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN);
            Identifier key = registry.getKey(demo.pattern().value());
            return key == null ? null : key.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** 纹饰图案注册键——直接取自 holder 自己（{@code unwrapKey()}），不依赖注册表身份表。 */
    private static String extractTrimPatternIdFromHolder(SlotDisplay slot) {
        if (!(slot instanceof SlotDisplay.SmithingTrimDemoSlotDisplay demo)) return null;
        return demo.pattern().unwrapKey()
                .map(key -> key.identifier().toString())
                .orElse(null);
    }

    /**
     * {@code smithing_trim} 配方的**模板物品 id**（取不到返回 null）。
     *
     * <p>去重的第二把钥匙：pattern holder 反查不出注册键时（Direct holder / 注册表实例不一致），
     * 模板物品仍然能把服务器条目和本地缓存条目对上（原版纹饰配方里模板与图案一一对应）。</p>
     */
    private static String extractTrimTemplateItemId(net.minecraft.world.item.crafting.display.RecipeDisplay display) {
        if (!(display instanceof net.minecraft.world.item.crafting.display.SmithingRecipeDisplay smithing)) {
            return null;
        }
        // 只对纹饰配方用（升级配方的 result 是物品，走上面那条路）
        if (!(smithing.result() instanceof SlotDisplay.SmithingTrimDemoSlotDisplay)) {
            return null;
        }
        return extractResultItemId(smithing.template());
    }

    private static void injectEntries(Map<RecipeDisplayId, RecipeDisplayEntry> known,
                                       Set<String> serverResultItems) {
        lastInjected.clear();
        lastFiltered.clear();
        lastCategoryBreakdown.clear();
        int nextId = -1;
        int injectedCount = 0;
        int skippedCount = 0;
        int filteredCount = 0;
        int trimSkippedByTemplate = 0;
        boolean complementMode = !serverResultItems.isEmpty();
        for (CacheableRecipeDisplayEntry cEntry : cache.values()) {
            try {
                // Match on resultItem alone — not category:resultItem — because
                // a datapack can change a recipe's category, and the server's
                // category is authoritative for the client recipe book UI.
                if (complementMode && cEntry.resultItem() != null) {
                    if (serverResultItems.contains(cEntry.resultItem())) {
                        skippedCount++;
                        continue;
                    }
                }
                // Trim recipes: dedupe by pattern (they have no result item;
                // otherwise every server trim would be duplicated with a
                // pattern-derived — but empty — product).
                //
                // ⚠️ 两把钥匙缺一不可（2026-09-28 用户反馈"纹饰页每个组都多一份一模一样的副本"）：
                // 1.21.11 上 pattern 那把钥匙**完全没进过 serverResultItems**（实测该次补全仍注入
                // 了 18 条 = 每个纹饰图案一条 → 锻造台纹饰页 18 组各多出一格同样的模板副本），
                // 于是这里再按**模板物品**兜一层——它只需要一个物品 id，不经过纹饰图案注册表。
                if (complementMode && cEntry.trimPattern() != null) {
                    if (serverResultItems.contains("trim:" + cEntry.trimPattern())) {
                        skippedCount++;
                        continue;
                    }
                    String trimTemplate = cEntry.trimTemplateItem();
                    if (trimTemplate != null && serverResultItems.contains("trimtmpl:" + trimTemplate)) {
                        trimSkippedByTemplate++;
                        skippedCount++;
                        continue;
                    }
                }
                String cat = cEntry.categoryName() != null ? cEntry.categoryName() : "unknown";
                RecipeDisplayId newId = new RecipeDisplayId(nextId--);
                RecipeDisplayEntry entry = cEntry.toEntry(newId);
                if (entry == null) { filteredCount++; continue; }
                List<ItemStack> results;
                if (cEntry.resultItem() != null) {
                    try { results = entry.resultItems(null); }
                    catch (Exception resEx) { results = List.of(); }
                    if (results.isEmpty() || results.stream().allMatch(s -> s == null || s.isEmpty())) {
                        filteredCount++;
                        if (lastFiltered.size() < SAMPLE_SIZE)
                            lastFiltered.add(cEntry.recipeKey() + " → " + cEntry.resultItem());
                        continue;
                    }
                }
                known.put(newId, entry);
                if (cEntry.recipeKey() != null) {
                    synchronized (injectedRecipeKeys) {
                        injectedRecipeKeys.put(newId.index(), cEntry.recipeKey());
                    }
                }
                injectedCount++;
                if (lastInjected.size() < SAMPLE_SIZE)
                    lastInjected.add(cEntry.recipeKey() + " → " + cEntry.resultItem());
                lastCategoryBreakdown.merge(cat, 1, Integer::sum);
            } catch (Exception e) {
                BetterRecipeBook.LOGGER.warn("[BRBE-CACHE] failed to inject entry {}: {}",
                        cEntry.recipeKey(), e.getMessage());
            }
        }
        String mode = complementMode ? "complement" : "all";
        BrbeLogger.log("BRBE-CACHE", "injected ({}): {} cached, {} skipped ({} of them trim-by-template), {} filtered (known now {})",
                mode, injectedCount, skippedCount, trimSkippedByTemplate, filteredCount, known.size());
        if (filteredCount > 0)
            BetterRecipeBook.LOGGER.warn("[BRBE-CACHE] filtered air entries (first {}): {}",
                    Math.min(SAMPLE_SIZE, lastFiltered.size()), lastFiltered);
        dumpStatus();
    }

    public static void dumpStatus() {
        BrbeLogger.log("BRBE", "========== [BRBE-CACHE] STATUS REPORT ==========");
        BrbeLogger.log("BRBE", "  Cache size: {}, server recipes in known: {}",
                cache.size(), lastServerCount);
        if (!lastInjected.isEmpty())
            BrbeLogger.log("BRBE", "  Injected (sample {}): {}", lastInjected.size(), lastInjected);
        if (!lastCategoryBreakdown.isEmpty())
            BrbeLogger.log("BRBE", "  By category (injected): {}", lastCategoryBreakdown);
        BrbeLogger.log("BRBE", "================================================");
    }

    static String extractResultItemId(SlotDisplay slot) {
        if (slot instanceof SlotDisplay.ItemSlotDisplay itemSlot)
            return BuiltInRegistries.ITEM.getKey(itemSlot.item().value()).toString();
        if (slot instanceof SlotDisplay.ItemStackSlotDisplay stackSlot)
            return BuiltInRegistries.ITEM.getKey(stackSlot.stack().item().value()).toString();
        return null;
    }

    public static boolean isLocalRecipe(RecipeDisplayId id) { return id.index() < 0; }
    public static boolean hasEntries() { return !cache.isEmpty(); }
    public static int cacheSize() { return cache.size(); }

    /** 缓存条目快照（配方命名空间索引用；顺序 = 加载顺序）。 */
    public static java.util.Collection<CacheableRecipeDisplayEntry> entries() {
        return List.copyOf(cache.values());
    }

    /** 当前缓存世代号（见 {@link #generation}）。 */
    public static int generation() { return generation; }

    // ---- Full dump for diff debugging ----

    /**
     * Dump every entry in known to the log in a compact, diffable format:
     * {@code [BRBE-DUMP] category source[id] resultItem}
     *
     * <p>Always-on.  Entries are sorted by category then result item so two
     * logs can be compared with standard diff tools to identify missing recipes.
     */
    public static void dumpAllKnown(Map<RecipeDisplayId, RecipeDisplayEntry> known) {
        // Always-on: lightweight structured log for diff debugging

        List<Map.Entry<RecipeDisplayId, RecipeDisplayEntry>> sorted =
                new ArrayList<>(known.entrySet());
        sorted.sort((a, b) -> {
            String catA = categoryKey(a.getValue().category());
            String catB = categoryKey(b.getValue().category());
            int cmp = catA.compareTo(catB);
            if (cmp != 0) return cmp;
            String itemA = java.util.Objects.toString(
                    extractResultItemId(a.getValue().display().result()), "");
            String itemB = java.util.Objects.toString(
                    extractResultItemId(b.getValue().display().result()), "");
            return itemA.compareTo(itemB);
        });

        BrbeLogger.log("BRBE-DUMP", "=== BEGIN {} entries ===", known.size());
        for (var entry : sorted) {
            RecipeDisplayId id = entry.getKey();
            RecipeDisplayEntry val = entry.getValue();
            String source = id.index() < 0 ? "cache" : "server";
            String cat = categoryKey(val.category());
            String result = extractResultItemId(val.display().result());
            if (result == null) result = "<no-item>";
            BrbeLogger.log("BRBE-DUMP", "{} {}[{}] {}",
                    cat, source, id.index(), result);
        }
        BrbeLogger.log("BRBE-DUMP", "=== END {} entries ===", known.size());
    }

    /**
     * Convert a RecipeBookCategory to a short, namespace-stripped key for
     * log output and comparison.
     */
    private static String categoryKey(net.minecraft.world.item.crafting.RecipeBookCategory category) {
        if (category == null) return "unknown";
        String key = BuiltInRegistries.RECIPE_BOOK_CATEGORY.getKey(category).toString();
        if (key.startsWith("minecraft:")) return key.substring("minecraft:".length());
        return key;
    }
}

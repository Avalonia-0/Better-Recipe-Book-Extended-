package com.alonie.recipebookispain_extended.mixin.groups;

import com.alonie.recipebookispain_extended.RecipeBookIsPain;
import com.alonie.recipebookispain_extended.RecipeBookIsPain.FurnaceVariant;
import com.alonie.recipebookispain_extended.RecipeBookIsPainExtendedConfig;
import com.alonie.recipebookispain_extended.access.ItemAccess;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.gui.screens.recipebook.SearchRecipeBookCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;
import net.minecraft.world.item.crafting.ExtendedRecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextMap;
import net.minecraft.util.context.ContextKeySet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import com.alonie.brbe.cache.RecipeNamespaceIndex;
import com.alonie.brbe.cache.RecipePackIndex;
import com.alonie.brbe.util.BrbeLogger;
import com.alonie.brbe.util.RecipeCraftingIndex;

@Mixin(value = ClientRecipeBook.class, priority = 999)
public class ClientRecipeBookMixin {

    @Shadow @Final private Map<RecipeDisplayId, RecipeDisplayEntry> known;
    @Shadow private Map<ExtendedRecipeBookCategory, List<RecipeCollection>> collectionsByTab;

    @Unique
    private static final ContextMap RBIP_EMPTY_CONTEXT = new ContextMap.Builder().create(new ContextKeySet.Builder().build());

    @Inject(at = @At("HEAD"), method = "rebuildCollections")
    private void rbip$preRefreshPolymerCache(CallbackInfo ci) {
        if (!RecipeBookIsPainExtendedConfig.enabled()) return;
        RecipeBookIsPain.buildNamespaceCache();
        RecipeBookIsPain.applyNamespaceOverrides();
    }

    // NOTE: the creative-group rebuild below must run on EVERY
    // rebuildCollections.  Vanilla rebuildCollections always resets
    // collectionsByTab to its own (RBIP-free) version first, so skipping
    // this rebuild while the known set is unchanged would leave
    // collectionsByTab without the mirrored creative-tab categories and every
    // RBIP tab would be hidden on the next updateTabs.  The per-packet
    // rebuild storm is handled upstream by
    // ClientPacketListenerMixin.brbe$skipUnchangedRefresh (cancels
    // refreshRecipeBook entirely while the known set is unchanged), so this
    // rebuild only runs when the known set actually changed.

    @Inject(at = @At("TAIL"), method = "rebuildCollections")
    private void rbip$refreshCreativeGroups(CallbackInfo ci) {
        if (!RecipeBookIsPainExtendedConfig.enabled()) return;
        RecipeBookIsPain.ensureInitialized();
        if (RecipeBookIsPain.RECIPE_BOOK_GROUP_TO_ITEM_GROUP.isEmpty()) return;

        RecipeBookIsPain.FURNACE_ACTIVE_TABS.clear();
        RecipeBookIsPain.SMOKER_ACTIVE_TABS.clear();
        RecipeBookIsPain.BLAST_FURNACE_ACTIVE_TABS.clear();
        RecipeBookIsPain.PACK_FURNACE_ACTIVE.clear();
        RecipeBookIsPain.PACK_SMOKER_ACTIVE.clear();
        RecipeBookIsPain.PACK_BLAST_ACTIVE.clear();
        RecipeBookIsPain.NS_FURNACE_ACTIVE.clear();
        RecipeBookIsPain.NS_SMOKER_ACTIVE.clear();
        RecipeBookIsPain.NS_BLAST_ACTIVE.clear();

        // 标签归组（三档，用户 2026-10-01 定）：
        //   创造模式物品栏档 = 每个创造标签一个配方书标签（原行为）；
        //   命名空间档       = 每个**配方 id 的命名空间**一个标签（联机时数据包档降级到这里）；
        //   数据包档         = 每个**来源数据包**一个标签（只有单机 / 局域网主机可用）。
        final boolean namespaceMode = RecipeBookIsPain.namespaceModeEnabled();
        final boolean packMode = RecipeBookIsPain.datapackModeEnabled();

        Map<ExtendedRecipeBookCategory, EntryBucket> buckets = new LinkedHashMap<>();
        Map<ExtendedRecipeBookCategory, EntryBucket> furnaceBuckets = new LinkedHashMap<>();
        Map<ExtendedRecipeBookCategory, EntryBucket> smokerBuckets = new LinkedHashMap<>();
        Map<ExtendedRecipeBookCategory, EntryBucket> blastFurnaceBuckets = new LinkedHashMap<>();

        for (RecipeDisplayEntry entry : this.known.values()) {
            RecipeDisplay display = entry.display();
            if (display instanceof StonecutterRecipeDisplay
                    || display instanceof SmithingRecipeDisplay) continue;

            // ⚠️ 这里**不得**按网格大小过滤配方（2026-09-25 修正）。
            // 本方法跑在 ClientRecipeBook.rebuildCollections 时机，只认 known 集合，
            // **看不到当前打开的是 2×2 背包还是 3×3 工作台**；此前在这里丢掉
            // "需要更大网格"的配方，导致：① RBIP 标签页在工作台上也永远不显示 3×3
            // 配方；② 唯一配方是 3×3 的创造标签组（如"刷怪蛋"里的嘎枝之心）整组为空
            // → 标签直接消失。
            // 网格可见性由**显示路径**按当前菜单判定：
            // pipeline/RecipeBookComponentMixin.brbe$applyGridVisibility（Stage 0）
            // —— 2×2 + showAllRecipesInSurvival=false 时把放不下的配方从
            // selected/craftable 剔除，3×3 时原样放行；RBIP 的合成组走同一条管线。

            if (display instanceof FurnaceRecipeDisplay) {
                // Determine which furnace type this recipe belongs to via its vanilla category
                FurnaceVariant variant = rbip$determineFurnaceVariant(entry.category());
                Map<ExtendedRecipeBookCategory, EntryBucket> targetBuckets = switch (variant) {
                    case SMOKER -> smokerBuckets;
                    case BLAST_FURNACE -> blastFurnaceBuckets;
                    default -> furnaceBuckets;
                };
                // Group by output item so recipes producing the same output merge into one button.
                // 产物解析不出来就跳过：命名空间模式按**配方 id**归组时，group 可能非空而
                // 产物为空（回退路径按产物归组则 group 也必然为空）。
                Iterator<ItemStack> results = entry.resultItems(RBIP_EMPTY_CONTEXT).iterator();
                if (!results.hasNext()) continue;
                String outputKey = BuiltInRegistries.ITEM.getKey(results.next().getItem()).toString();
                if (namespaceMode) {
                    ExtendedRecipeBookCategory group = rbip$getNamespaceGroupForEntry(entry, variant);
                    if (group == null) continue;
                    rbip$bucketFor(targetBuckets, group).add(entry, outputKey);
                } else if (packMode) {
                    // 同一条配方可能被多个包提供（覆盖）：每个提供者的标签各放一份
                    for (ExtendedRecipeBookCategory group : rbip$getCompactGroupsForEntry(entry, variant)) {
                        rbip$bucketFor(targetBuckets, group).add(entry, outputKey);
                    }
                } else {
                    // 创造模式档：产物出现在几个创造标签里就进几个标签（用户 2026-10-01）
                    rbip$addFurnaceCreativeEntry(targetBuckets, entry, variant, outputKey);
                }
            } else {
                // Only include recipes belonging to the vanilla crafting recipe book.
                // Mod-added recipe books (e.g. Farmer's Delight cooking pot) have custom
                // RecipeBookCategory instances that differ from the vanilla crafting ones.
                ExtendedRecipeBookCategory cat = entry.category();
                if (cat != RecipeBookCategories.CRAFTING_BUILDING_BLOCKS
                        && cat != RecipeBookCategories.CRAFTING_REDSTONE
                        && cat != RecipeBookCategories.CRAFTING_EQUIPMENT
                        && cat != RecipeBookCategories.CRAFTING_MISC
                        && cat != SearchRecipeBookCategory.CRAFTING) continue;

                if (namespaceMode) {
                    ExtendedRecipeBookCategory group = rbip$getNamespaceGroupForEntry(entry, null);
                    if (group == null) continue;
                    rbip$bucketFor(buckets, group).add(entry);
                } else if (packMode) {
                    // 同一条配方可能被多个包提供（覆盖）：每个提供者的标签各放一份
                    for (ExtendedRecipeBookCategory group : rbip$getCompactGroupsForEntry(entry, null)) {
                        rbip$bucketFor(buckets, group).add(entry);
                    }
                } else {
                    // 创造模式档：产物出现在几个创造标签里就进几个标签（用户 2026-10-01）
                    rbip$addCreativeEntry(buckets, entry);
                }
            }
        }

        if (buckets.isEmpty() && furnaceBuckets.isEmpty()
                && smokerBuckets.isEmpty() && blastFurnaceBuckets.isEmpty()) return;

        Map<ExtendedRecipeBookCategory, List<RecipeCollection>> updatedResults = new HashMap<>(this.collectionsByTab);
        // 扩展档（命名空间 / 数据包）下原版熔炉分类都可以删：它们的标签栏里只有搜索标签 +
        // 命名空间 / 数据包标签，原版熔炉分类留下的只是永远不被显示的集合。
        if (!packMode && !namespaceMode) {
            updatedResults.remove(RecipeBookCategories.FURNACE_FOOD);
            updatedResults.remove(RecipeBookCategories.FURNACE_BLOCKS);
            updatedResults.remove(RecipeBookCategories.FURNACE_MISC);
            updatedResults.remove(RecipeBookCategories.SMOKER_FOOD);
            updatedResults.remove(RecipeBookCategories.BLAST_FURNACE_BLOCKS);
            updatedResults.remove(RecipeBookCategories.BLAST_FURNACE_MISC);
        }

        for (Map.Entry<ExtendedRecipeBookCategory, EntryBucket> e : buckets.entrySet()) {
            updatedResults.put(e.getKey(), e.getValue().toCollections());
        }
        for (Map.Entry<ExtendedRecipeBookCategory, EntryBucket> e : furnaceBuckets.entrySet()) {
            updatedResults.put(e.getKey(), e.getValue().toCollections());
        }
        for (Map.Entry<ExtendedRecipeBookCategory, EntryBucket> e : smokerBuckets.entrySet()) {
            updatedResults.put(e.getKey(), e.getValue().toCollections());
        }
        for (Map.Entry<ExtendedRecipeBookCategory, EntryBucket> e : blastFurnaceBuckets.entrySet()) {
            updatedResults.put(e.getKey(), e.getValue().toCollections());
        }

        // 紧凑模式（2026-09-30 起）**不需要**"原版标签只留原版配方"的逐条过滤了：原版配方
        // 也独占一个标签（{@code minecraft} 命名空间，图标草方块），原版配方书标签整个不再
        // 出现在标签栏里（见 RecipeBookIsPain.compactBaseTabs），两处重复的问题自然消失。

        this.collectionsByTab = Map.copyOf(updatedResults);

        // 增量 canCraft 索引（RecipeCraftingIndex）必须覆盖**最终显示用的集合**：上面这些
        // RecipeCollection 都是新对象，不在 vanilla 的 allCollections 里；只按
        // allCollections 建索引会让 shouldSkip 把它们误判成"不受库存变化影响" → 取消
        // selectRecipes → 该集合的 craftable/selected 冻结（2026-09-30 用户反馈：选中
        // RBIP 标签页时用最后一个铁锭合成铁粒后，配方仍显示可合成、材料却全部标记缺失；
        // 搜索页用的是 vanilla 集合所以看不出来）。brbe 侧
        // localcache/ClientRecipeBookMixin 也会重建一次（输入 = allCollections ∪
        // collectionsByTab），两处互为兜底，与两个 mixin 的注入顺序无关。
        RecipeCraftingIndex.rebuild(rbip$flattenCollections(updatedResults));
    }

    /**
     * {@code get} + 按需 {@code put}（不用 {@code computeIfAbsent}）。
     *
     * <p>Mixin 类里**不要写 lambda**：Mixin 会把 mixin 内的合成 lambda 方法重命名并在
     * latest.log 打一行 INFO（{@code Renaming synthetic method ... from mod brbe}），
     * 每个 lambda 一行——本类原先 6 个 lambda 就是 6 行噪声。</p>
     */
    @Unique
    private static EntryBucket rbip$bucketFor(Map<ExtendedRecipeBookCategory, EntryBucket> map,
                                              ExtendedRecipeBookCategory group) {
        EntryBucket bucket = map.get(group);
        if (bucket == null) {
            bucket = new EntryBucket();
            map.put(group, bucket);
        }
        return bucket;
    }

    @Unique
    private static FurnaceVariant rbip$determineFurnaceVariant(RecipeBookCategory category) {
        if (category == RecipeBookCategories.SMOKER_FOOD) return FurnaceVariant.SMOKER;
        if (category == RecipeBookCategories.BLAST_FURNACE_BLOCKS
                || category == RecipeBookCategories.BLAST_FURNACE_MISC) return FurnaceVariant.BLAST_FURNACE;
        return FurnaceVariant.FURNACE;
    }

    /**
     * 创造模式档归组（合成 / 酿造）：把条目加进**产物所属的每一个**创造标签对应的分类。
     *
     * <p>原版创造模式允许同一物品挂在多个标签下（木桶 = 功能方块 + 红石方块），而 RBIP 原来
     * 只挑一个 → 配方书里木桶配方只出现在「功能方块」（用户 2026-10-01 反馈）。用户定
     * 「还原优先于去重」：一条配方进多个标签可以接受。只看**第一个**能解析出标签的产物
     * （与旧的单标签行为一致），产物一个标签都解析不出来时直接丢弃。</p>
     */
    @Unique
    private static void rbip$addCreativeEntry(Map<ExtendedRecipeBookCategory, EntryBucket> buckets,
                                              RecipeDisplayEntry entry) {
        try {
            for (ItemStack stack : entry.resultItems(RBIP_EMPTY_CONTEXT)) {
                List<ExtendedRecipeBookCategory> groups = RecipeBookIsPain.toRecipeBookGroups(stack);
                if (groups.isEmpty()) continue;
                for (ExtendedRecipeBookCategory group : groups) {
                    rbip$bucketFor(buckets, group).add(entry);
                }
                return;
            }
        } catch (Exception e) {
            BrbeLogger.log("RBIP", "Could not resolve output stack for recipe display {}", entry.id(), e);
        }
    }

    /**
     * 「命名空间」模式的归属：**配方 id 的命名空间** → 该命名空间的标签
     * （{@code minecraft} / 每个模组 / 每个数据包各一个）。联机时数据包档降级到这一档，
     * 所以它是**唯一在联机下也可用**的扩展档（用户 2026-10-01 定）。
     *
     * <p><b>归组键 = 配方的「产物」的命名空间</b>（用户 2026-10-02 修订，核心依据）：
     * {@code entry.resultItems(...)} 里第一个能解析出注册 id 的产物 → 它的命名空间。
     * 这样"数据包/模组给原版物品加配方"会自然并进原版标签（兼容性语义）。</p>
     *
     * <p>产物解析不出来（特殊配方 / 无产物显示）时才退回**配方 id 的命名空间**
     * （{@link RecipeNamespaceIndex#namespaceOf}，display 值相等反查）；两者都没有 →
     * {@link RecipeBookIsPain#NS_UNKNOWN_KEY}「未知来源」标签。</p>
     *
     * <p>顺带把产物丢进该标签的**图标随机池**（用户 2026-10-01 定：图标 = 稳定伪随机挑一个
     * 标签内配方的产物；{@code minecraft} 保留草方块特例）。</p>
     *
     * <p>{@code variant} 为 {@code null} = 合成台；否则同时把该命名空间记进对应熔炉类型的
     * "有配方" 集合，供标签栏过滤掉空标签。</p>
     */
    @Unique
    private static ExtendedRecipeBookCategory rbip$getNamespaceGroupForEntry(RecipeDisplayEntry entry, FurnaceVariant variant) {
        try {
            // ① 核心依据：**产物的命名空间**（用户 2026-10-02 修订；此前是"配方 id 的命名空间优先"）
            ExtendedRecipeBookCategory group = null;
            for (ItemStack stack : entry.resultItems(RBIP_EMPTY_CONTEXT)) {
                group = RecipeBookIsPain.toNamespaceGroup(stack, variant);
                if (group == null) continue;
                RecipeBookIsPain.rememberNamespaceProduct(RecipeBookIsPain.namespaceKey(group), stack);
                break;      // 产物进图标随机池
            }
            // ② 产物解析不出来（特殊配方 / 无产物）：退回配方 id 的命名空间
            if (group == null) {
                group = RecipeBookIsPain.namespaceGroupFor(
                        RecipeBookIsPain.namespaceKeyOfRecipe(entry.display()), variant);
            }
            if (group == null) {
                // 连配方 id 都反查不到：进「未知来源」标签，配方不凭空消失
                group = RecipeBookIsPain.namespaceGroupFor(RecipeBookIsPain.NS_UNKNOWN_KEY, variant);
            }
            if (group == null) return null;
            String key = RecipeBookIsPain.namespaceKey(group);
            if (key != null) {
                if (variant == FurnaceVariant.SMOKER) {
                    RecipeBookIsPain.NS_SMOKER_ACTIVE.add(key);
                } else if (variant == FurnaceVariant.BLAST_FURNACE) {
                    RecipeBookIsPain.NS_BLAST_ACTIVE.add(key);
                } else if (variant == FurnaceVariant.FURNACE) {
                    RecipeBookIsPain.NS_FURNACE_ACTIVE.add(key);
                }
            }
            return group;
        } catch (Exception e) {
            BrbeLogger.log("RBIP", "Could not resolve namespace group for {}", entry.id(), e);
        }
        return null;
    }

    /**
     * 「数据包」模式的归属：**配方来自哪个数据包** → 该数据包的标签
     * （原版 / 模组自带数据包 / 每个启用的数据包各一个）。联机拿不到服务端包列表 →
     * 全部进 {@link RecipePackIndex#UNKNOWN_KEY} 一个「服务器」标签（用户 2026-10-01 定）。
     *
     * <p>主路径：{@link RecipeNamespaceIndex#recipeIdOfEntry} 反查配方 id —— 注入条目（负 id）
     * 直接用本地缓存记下的配方 key，其余走 display 值相等（与给锻造/切石条目挂 JEI layout
     * 同一套判据；注入条目的 display 是本地重建的、必然对不上，2026-10-02 实测）→
     * {@link RecipePackIndex#packKeyOf} 查来源包；单机未命中时退回**产物物品命名空间**的归属包。</p>
     *
     * <p>顺带把产物丢进该标签的**图标随机池**（用户 2026-10-01 定：图标 = 稳定伪随机挑一个
     * 标签内配方的产物；原版保留草方块特例）。</p>
     *
     * <p>{@code variant} 为 {@code null} = 合成台；否则同时把该包记进对应熔炉类型的
     * "有配方" 集合，供标签栏过滤掉空标签。</p>
     */
    @Unique
    private static java.util.List<ExtendedRecipeBookCategory> rbip$getCompactGroupsForEntry(
            RecipeDisplayEntry entry, FurnaceVariant variant) {
        try {
            java.util.List<ExtendedRecipeBookCategory> groups = new java.util.ArrayList<>();
            for (String k : RecipeBookIsPain.packKeysOfRecipeEntry(entry)) {
                ExtendedRecipeBookCategory g = RecipeBookIsPain.packGroupFor(k, variant);
                if (g != null) groups.add(g);
            }
            java.util.List<ItemStack> products = entry.resultItems(RBIP_EMPTY_CONTEXT);
            if (!groups.isEmpty()) {
                for (ExtendedRecipeBookCategory g : groups) {          // 产物进每个标签的图标池
                    for (ItemStack stack : products) {
                        RecipeBookIsPain.rememberPackProduct(RecipeBookIsPain.packKey(g), stack);
                        break;
                    }
                }
            } else {
                for (ItemStack stack : products) {                     // 退回产物命名空间
                    ExtendedRecipeBookCategory g = RecipeBookIsPain.toPackGroup(stack, variant);
                    if (g == null) continue;
                    RecipeBookIsPain.rememberPackProduct(RecipeBookIsPain.packKey(g), stack);
                    groups.add(g);
                    break;
                }
                if (groups.isEmpty()) {
                    ExtendedRecipeBookCategory g =
                            RecipeBookIsPain.packGroupFor(RecipePackIndex.UNKNOWN_KEY, variant);
                    if (g != null) groups.add(g);
                }
            }
            for (ExtendedRecipeBookCategory group : groups) {
                String key = RecipeBookIsPain.packKey(group);
                if (key == null) continue;
                if (variant == FurnaceVariant.SMOKER) {
                    RecipeBookIsPain.PACK_SMOKER_ACTIVE.add(key);
                } else if (variant == FurnaceVariant.BLAST_FURNACE) {
                    RecipeBookIsPain.PACK_BLAST_ACTIVE.add(key);
                } else if (variant == FurnaceVariant.FURNACE) {
                    RecipeBookIsPain.PACK_FURNACE_ACTIVE.add(key);
                }
            }
            return groups;
        } catch (Exception e) {
            BrbeLogger.log("RBIP", "Could not resolve pack groups for {}", entry.id(), e);
        }
        return java.util.List.of();
    }

    /**
     * 创造模式档归组（熔炉系）：同 {@link #rbip$addCreativeEntry}，但分类走熔炉那套映射，
     * 并把每个命中的标签记进对应熔炉类型的 active 集合（标签栏据此过滤掉空标签）。
     */
    @Unique
    private static void rbip$addFurnaceCreativeEntry(Map<ExtendedRecipeBookCategory, EntryBucket> buckets,
                                                     RecipeDisplayEntry entry, FurnaceVariant variant,
                                                     String outputKey) {
        try {
            for (ItemStack stack : entry.resultItems(RBIP_EMPTY_CONTEXT)) {
                List<ExtendedRecipeBookCategory> groups =
                        RecipeBookIsPain.toFurnaceRecipeBookGroups(stack, variant);
                if (groups.isEmpty()) continue;
                for (ExtendedRecipeBookCategory group : groups) {
                    rbip$bucketFor(buckets, group).add(entry, outputKey);
                    CreativeModeTab tab = switch (variant) {
                        case SMOKER -> RecipeBookIsPain.SMOKER_BOOK_GROUP_TO_ITEM_GROUP.get(group);
                        case BLAST_FURNACE -> RecipeBookIsPain.BLAST_FURNACE_BOOK_GROUP_TO_ITEM_GROUP.get(group);
                        default -> RecipeBookIsPain.FURNACE_BOOK_GROUP_TO_ITEM_GROUP.get(group);
                    };
                    if (tab != null) {
                        switch (variant) {
                            case SMOKER -> RecipeBookIsPain.SMOKER_ACTIVE_TABS.add(tab);
                            case BLAST_FURNACE -> RecipeBookIsPain.BLAST_FURNACE_ACTIVE_TABS.add(tab);
                            default -> RecipeBookIsPain.FURNACE_ACTIVE_TABS.add(tab);
                        }
                    }
                }
                return;
            }
        } catch (Exception e) {
            BrbeLogger.log("RBIP", "Could not resolve furnace output for {}", entry.id(), e);
        }
    }

    /** {@code collectionsByTab} 里的全部集合——索引重建的输入。 */
    @Unique
    private static List<RecipeCollection> rbip$flattenCollections(
            Map<ExtendedRecipeBookCategory, List<RecipeCollection>> byTab) {
        List<RecipeCollection> out = new ArrayList<>();
        for (List<RecipeCollection> collections : byTab.values()) {
            if (collections != null) out.addAll(collections);
        }
        return out;
    }

    private static class EntryBucket {
        private final List<List<RecipeDisplayEntry>> entries = new ArrayList<>();
        private final Map<Integer, List<RecipeDisplayEntry>> groupedEntries = new LinkedHashMap<>();
        private final Map<String, List<RecipeDisplayEntry>> groupedByOutput = new LinkedHashMap<>();

        private void add(RecipeDisplayEntry entry) {
            OptionalInt group = entry.group();
            if (group.isEmpty()) {
                this.entries.add(List.of(entry));
                return;
            }
            List<RecipeDisplayEntry> grouped = this.groupedEntries.get(group.getAsInt());
            if (grouped == null) {
                grouped = new ArrayList<>();
                this.groupedEntries.put(group.getAsInt(), grouped);
                this.entries.add(grouped);
            }
            grouped.add(entry);
        }

        private void add(RecipeDisplayEntry entry, String outputKey) {
            List<RecipeDisplayEntry> grouped = this.groupedByOutput.get(outputKey);
            if (grouped == null) {
                grouped = new ArrayList<>();
                this.groupedByOutput.put(outputKey, grouped);
                this.entries.add(grouped);
            } else {
                RecipeDisplay newDisplay = entry.display();
                if (newDisplay instanceof FurnaceRecipeDisplay newFd) {
                    for (RecipeDisplayEntry existing : grouped) {
                        if (existing.display() instanceof FurnaceRecipeDisplay existingFd) {
                            if (newFd.ingredient().equals(existingFd.ingredient())
                                    && newFd.result().equals(existingFd.result())) {
                                return; // Same ingredient + result → skip
                            }
                        }
                    }
                } else {
                    // Non-furnace display: use standard equals
                    for (RecipeDisplayEntry existing : grouped) {
                        if (newDisplay.equals(existing.display())) return;
                    }
                }
            }
            grouped.add(entry);
        }

        private List<RecipeCollection> toCollections() {
            // 用显式循环而不是 stream+lambda：mixin 内的 lambda 会被 Mixin 重命名并刷
            // 一行 INFO（见 rbip$bucketFor 的说明）。
            List<RecipeCollection> out = new ArrayList<>(this.entries.size());
            for (List<RecipeDisplayEntry> group : this.entries) {
                out.add(new RecipeCollection(List.copyOf(group)));
            }
            return List.copyOf(out);
        }
    }
}

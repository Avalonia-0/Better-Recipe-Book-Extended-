package com.alonie.brbe.mixins.pipeline;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeBookComponentAccessor;
import com.alonie.brbe.mixins.accessors.RecipeBookPageAccessor;
import com.alonie.brbe.mixins.accessors.RecipeCollectionAccessor;
import com.alonie.brbe.search.SearchQuery;
import com.alonie.brbe.util.CollectionPipeline;
import com.alonie.brbe.util.FusedRecipeVariants;
import com.alonie.brbe.util.PartialCraftingUtil;
import com.alonie.brbe.util.RecipeBookPositionMemory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.ArrayList;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Unified pipeline for {@code RecipeBookComponent.updateCollections()}.
 *
 * <p>Replaces the four previously-scattered {@code @ModifyArg}/ {@code @Redirect}
 * handlers (search, ungroup, pins, incompletecrafting-sort) with a single
 * deterministic pipeline.  Pipeline order is defined in
 * {@link CollectionPipeline} and is:
 * <ol>
 *   <li>Advanced search filter</li>
 *   <li>Ungroup split (alternativeRecipes.splitMode == 完全)</li>
 *   <li>Pins sort (pinned → front)</li>
 *   <li>Partial sort (craftable → partial → uncraftable)</li>
 * </ol>
 *
 * <p>Also owns the search-text save/restore injects (moved from the
 * search package mixin, which is now retired).
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin {

    @Shadow @Final protected Minecraft minecraft;

    @Shadow protected EditBox searchBox;

    @SuppressWarnings("rawtypes")
    @Shadow
    protected RecipeBookMenu menu;

    /** 原版当前物品栏的 stacked contents（判定"哪个融合成员真能做"）。 */
    @Shadow
    @Final
    private StackedItemContents stackedContents;

    @Unique
    private String brbe$savedSearchText;

    @Unique
    private SearchQuery brbe$parsedQuery;

    // ---- Pipeline output cache ----
    // When the inventory is unchanged (RecipeCraftingIndex.inventoryUnchanged),
    // every collection's canCraft/craftable state is identical to the last
    // pass, so pins order + partial sort produce the same list.  Reusing the
    // cached list skips the two O(collections) stages (applyPins + partial
    // sort's categorize) — the dominant cost of every recipe-book open on
    // large recipe packs.  Invalidated on inventory change, pin set change
    // (PinnedRecipeManager.version), search query change (the query TEXT —
    // see brbe$cacheSearchText), isFiltering change (vanilla filters the list
    // before this hook — see brbe$cacheIsFiltering), config change, or
    // collection rebuild (RecipeCraftingIndex.generation).
    //
    // ⚠️ 搜索词必须是缓存键的一部分：Stage 1 的输出是搜索词的函数，把它退化成
    // "有没有搜索"的布尔量会让「空→非空」后的每一次改词都命中缓存、返回上一次
    // 查询的结果（高级语法冻结在 1 字符前缀 "@"/"$"/"#"/"r" → 匹配为空 → 空页）。

    @Unique
    private List<RecipeCollection> brbe$cachedPipelinedList;

    /**
     * **管线输出缓存的键**（唯一判据）：{@link #brbe$pipelineFingerprint} 的结果。
     *
     * <p>键从"手写一串代理量"改成**一个指纹**：指纹里既有"上游生产者是否动过"的纪元
     * （{@link com.alonie.brbe.util.PipelineEpoch}），也有**输入数据本身**的哈希
     * （集合身份 + craftable/selected + 残缺标记，见
     * {@link PartialCraftingUtil#pipelineStateHash(java.util.List)}）。
     * 于是"漏加一个键"不再会静默返回过期结果 —— 这正是本缓存出过三次 Bug 的根因
     * （2026-09-08 搜索词 / 2026-09-11 {@code isFiltering} / 2026-09-13 残缺标记重算）。 */
    @Unique
    private int brbe$cacheFingerprint;

    @Unique
    private int brbe$cacheGeneration = -1;

    @Unique
    private int brbe$cachePinVersion = -1;

    /**
     * 上一次管线输出所对应的**搜索词原文**（HEAD 捕获的 {@link #brbe$savedSearchText}，
     * 无搜索时为 {@code ""}）。缓存键必须包含它——搜索命中集合是搜索词的函数，
     * 只有"有没有搜索"一个布尔量会让改词后的管线输出被错误复用。
     */
    @Unique
    private String brbe$cacheSearchText = "";

    @Unique
    private boolean brbe$cacheConfigKey;

    @Unique
    private boolean brbe$cacheHasPipelined;

    /** 上一次写进日志的合并摘要（去重，避免每次库存变化都打一行）。见 {@link #brbe$logMergeStats()}。 */
    @Unique
    private String brbe$lastMergeLog;

    /** 缓存键：残缺标记修订号（{@link com.alonie.brbe.util.PartialCraftingUtil#partialMarkingRevision()}）。
     *  Stage 4 排序依赖残缺标记，而标记会在库存没变时被整轮重算（集合重建 / 配置变化 /
     *  物品栏界面的强制 pass）→ 必须入键，否则排序会用过期顺序（用户 2026-09-13 反馈）。 */
    @Unique
    private int brbe$cacheMarkRevision = -1;

    /** 缓存键：{@link com.alonie.brbe.util.RecipeCraftingIndex#currentVersion()} —— 库存内容
     *  变化或索引重建时自增，比 {@code inventoryUnchanged()} 更可靠（后者是"上一次 beginPass
     *  的 diff 为空"的全局静态标记，集合重建时会被重置成"没变"的假象）。 */
    @Unique
    private int brbe$cacheIndexVersion = -1;

    /**
     * 上一次管线输出对应的 {@code isFiltering}（"仅显示可合成"）。
     * <p>
     * 该值决定 **vanilla 在管线之前**对列表做的过滤
     * （{@code if (isFiltering) removeIf(!hasCraftable())}），所以管线输出依赖它。
     * 漏进缓存键的后果：切换过滤器后 vanilla 已把不可合成集合剔除，但缓存命中
     * 会把上一轮（未过滤）的列表交回页面——应被隐藏的配方以**空按钮/空气占位符**
     * 出现，点击该按钮时 {@code RecipeButton.getCurrentRecipe()} 除以 0 崩溃
     * （BRBE 的 RecipeButtonSafetyMixin 只兜住了渲染路径）。
     */
    @Unique
    private boolean brbe$cacheIsFiltering;

    /**
     * 本次管线调用对应的搜索词原文。{@code brbe$runPipeline} 期间搜索框已被清空
     * （HEAD 的 {@code brbe$saveSearchText}），{@link #brbe$parsedQuery} 本身也不携带
     * 原文——因此只能取 HEAD 保存的 {@link #brbe$savedSearchText}。
     */
    @Unique
    private String brbe$currentSearchText() {
        return brbe$savedSearchText == null ? "" : brbe$savedSearchText;
    }

    @Unique
    private boolean brbe$configKey() {
        return brbe$configMask() != 0;
    }

    /**
     * **配置位掩码**（管线输出缓存的键分量之一）：每个开关**单独**占一位。
     *
     * <p>不能压成一个 boolean OR —— 那样只要有一个开关是开的，切换其余开关就不会改变键
     * （{@code mergeSameResult} 默认开 → OR 恒真 → 关掉「显示部分可合成配方」时管线不会重跑）。
     * 2026-09-30 由 {@code tools/search-cache-harness} 的 Phase D/E 抓出（"配置变 → 指纹变"）。</p>
     */
    @Unique
    private int brbe$configMask() {
        if (BetterRecipeBook.config == null) return 0;
        int mask = 0;
        if (BetterRecipeBook.config.partialCraftingEnabled) mask |= 1;
        if (BetterRecipeBook.config.partialMarkingEnabled) mask |= 2;
        if (BetterRecipeBook.config.alternativeRecipes.noGrouped()) mask |= 4;
        // 「拆散替代配方组」三档必须**分位**（选择性＝0 位 / 完全＝4 / 关闭＝16）：压成一个布尔
        // 会让档位切换不改变缓存键 —— 与 2026-09-30 那次"开关压成一个 OR"同源的坑。
        if (!BetterRecipeBook.config.alternativeRecipes.selectiveSplitEnabled()) mask |= 16;
        // 同产物合并会改变管线输出的分组 → 必须进缓存键，否则开关切换不生效
        // （同 2026-09-11 搜索词 / isFiltering 那两轮踩过的坑）
        if (BetterRecipeBook.config.alternativeRecipes.mergeSameResult) mask |= 8;
        return mask;
    }

    // ---- Search text save / restore ----

    /**
     * 右键点击搜索框时清空搜索文字、取消聚焦并刷新。
     *
     * <p>刷新用非重置模式（{@code resetPageNumber=false}）：清空搜索不把页码
     * 打回第 1 页；随后恢复该标签搜索前的浏览页码（"保存浏览记录"功能）。</p>
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void brbe$rightClickClearSearch(MouseButtonEvent event, boolean doubled,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (!com.alonie.brbe.util.ClientCompat.isRightClick(event) || searchBox == null) return;
        if (!searchBox.isMouseOver(event.x(), event.y())) return;
        searchBox.setValue("");
        searchBox.setFocused(false);
        ((RecipeBookComponentAccessor) this).updateCollectionsInvoker(false, false);
        brbe$restorePageAfterSearchClear();
        cir.setReturnValue(true);
    }

    /**
     * 搜索词清空后恢复该标签搜索前的浏览页码：页码来自记忆中的 basePage
     * （空搜索状态下持续更新的页码），钳制到当前列表范围。
     */
    @Unique
    private void brbe$restorePageAfterSearchClear() {
        if (!BetterRecipeBook.config.saveRecipeBookPosition) return;
        RecipeBookComponentAccessor acc = (RecipeBookComponentAccessor) this;
        RecipeBookTabButton tab = acc.getSelectedTab();
        if (tab == null) return;
        int tabIndex = acc.getTabButtons().indexOf(tab);
        if (tabIndex < 0) return;
        RecipeBookPositionMemory.Pos pos = RecipeBookPositionMemory.load(bookKey(), tabIndex);
        if (pos == null) return;
        RecipeBookPage page = acc.getRecipeBookPage();
        RecipeBookPageAccessor pageAcc = (RecipeBookPageAccessor) page;
        int max = Math.max(0, pageAcc.getTotalPages() - 1);
        pageAcc.setCurrentPage(Math.min(pos.basePage(), max));
        pageAcc.updateButtonsForPageInvoker();
    }

    /**
     * Stage 0a: At HEAD, detect advanced search syntax.
     * If found, save and clear the search box so vanilla's substring
     * filter becomes a no-op.
     */
    @Inject(method = "updateCollections", at = @At("HEAD"))
    private void brbe$saveSearchText(boolean resetPageNumber, boolean isFiltering, CallbackInfo ci) {
        brbe$savedSearchText = null;
        brbe$parsedQuery = null;

        if (searchBox == null) return;

        String text = searchBox.getValue();
        if (text == null || text.isEmpty()) return;

        SearchQuery query = SearchQuery.parse(text);
        brbe$savedSearchText = text;
        brbe$parsedQuery = query;
        searchBox.setValue("");
    }

    /**
     * Stage 0b: At TAIL, restore the search box text if we cleared it.
     */
    @Inject(method = "updateCollections", at = @At("TAIL"))
    private void brbe$restoreSearchText(boolean resetPageNumber, boolean isFiltering, CallbackInfo ci) {
        if (brbe$savedSearchText != null && searchBox != null) {
            searchBox.setValue(brbe$savedSearchText);
            brbe$savedSearchText = null;
            brbe$parsedQuery = null;
        }
    }

    // ---- Config-change reload ----

    /**
     * Config-change-driven recipe book reload — equivalent to reopening
     * the recipe book.
     *
     * <p>Vanilla {@code tick()} only calls {@code updateStackedContents} →
     * {@code updateCollections} when the inventory changes.  If a config
     * toggle flips while the player is looking at the recipe book, the
     * change would go unnoticed until the next inventory change.  This hook
     * detects pending config changes and proactively calls
     * {@code updateStackedContents()}, which runs the full three-step
     * refresh: clear+refill stackedContents → selectMatchingRecipes (clears
     * and repopulates craftable sets) → updateCollections (filter+sort+pipeline).
     * The {@code keepPartiallyCraftable} redirect consumes the config-change
     * flag during this call and performs a full re-marking pass.
     */
    @Inject(method = "tick", at = @At("RETURN"))
    private void brbe$reloadOnConfigChange(CallbackInfo ci) {
        if (BetterRecipeBook.ctx() == null) return;
        if (!BetterRecipeBook.ctx().events().hasPendingConfigChange()) return;
        // Only trigger when the recipe book is actually visible.
        // Otherwise the next open will trigger initVisuals()->updateCollections()
        // which naturally rebuilds everything.
        if (!((RecipeBookComponent)(Object)this).isVisible()) return;
        // Full refresh path: updateStackedContents triggers
        // selectMatchingRecipes → updateCollections pipeline.
        ((RecipeBookComponentAccessor)this).updateStackedContentsInvoker();
    }

    // ---- Pipeline ----

    /**
     * Replaces the {@code page.updateCollections(list, …)} call with the
     * deterministic pipeline.  Each stage is a pure function defined in
     * {@link CollectionPipeline}.
     */
    @Redirect(method = "updateCollections",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookPage;updateCollections(Ljava/util/List;ZZ)V"))
    private void brbe$runPipeline(RecipeBookPage page, List<RecipeCollection> list,
                                   boolean resetPageNumber, boolean isFiltering) {

        // ---- Stage 0: 网格可见性（显示路径的兜底，先于指纹/缓存）----
        // 2×2 网格 + showAllRecipesInSurvival=OFF 时，3×3 配方必须从 selected /
        // craftable 里剔除，只剩 3×3 的集合整组丢弃。挂在**显示路径**上意味着
        // 无论 selected 是否被重算（增量索引跳过 / 未重算的路径），显示结果都对。
        // 语义与 1.21.1 的 RecipePipeline.applyVisibility 一致。
        list = brbe$applyGridVisibility(list);

        // ---- Stage 0b: 进度可见性（同一显示路径兜底）----
        // unlockAll 关闭 = 只显示进度系统（advancement rewards.recipes）解锁的配方：
        // 任何"解锁全部"模组给服务端配方书塞进来的配方都在这里被剔除（它们不是
        // BRBE 的注入，撤销管不到；详见 ProgressionUnlocks 的类注释）。
        list = brbe$applyProgressionVisibility(list);

        // ---- Pipeline output cache ----
        // 键 = **单一指纹**（见 brbe$cacheFingerprint 的注释）：命中判据从十几个代理量
        // 收敛成一次 int 比较，而指纹同时覆盖"生产者纪元"与"输入数据实际内容"。
        // resetPageNumber=true（切标签/重开配方书）一律重算。
        int brbe$fingerprint = brbe$pipelineFingerprint(list, isFiltering);
        boolean cacheHit = false;
        if (brbe$cacheHasPipelined
                && !resetPageNumber
                && brbe$fingerprint == brbe$cacheFingerprint) {
            // 缓存的是管线输出**原样快照**（浅拷贝）：Stage 6 会原地改写传入列表
            // （移除原组/插入重打包组），若直接复用同一对象，缓存里就只残留重打包
            // 组，下一次命中时原组无处还原。每次取出拷贝后由 Stage 6 重建。
            List<RecipeCollection> cachedSnapshot = brbe$cachedPipelinedList;
            // [诊断] 命中时用当前输入重算一遍，比对"输入来源元素"的顺序 ——
            // 不一致 = 有输入没进指纹（当场 ERROR，而不是等玩家报"界面不对"）。
            if (com.alonie.brbe.util.RecipeStateDiagnostic.enabled()) {
                brbe$verifyCacheHit(list, cachedSnapshot, brbe$fingerprint);
            }
            list = new ArrayList<>(cachedSnapshot);
            cacheHit = true;
        }

        if (!cacheHit) {
            // Stage 1: Advanced search filter
            if (brbe$parsedQuery != null && minecraft.level != null) {
                list = CollectionPipeline.applySearch(
                        list, brbe$parsedQuery,
                        SlotDisplayContext.fromLevel(minecraft.level));
            }

            // Stage 2: Ungroup split (if splitMode == 完全)
            list = CollectionPipeline.applyUngroup(list);

            // Stage 2.6: 同产物合并（用户 2026-09-28 诉求，配置 alternativeRecipes.mergeSameResult）
            // 产物 = 物品 + 组件（忽略数量）相同的集合并成一个替代配方组；取消分组开启时本项不生效。
            // 合并后照旧参与 Stage 2.5 的排序剥离（pin/可合成/残缺仍会剥出去置顶）。
            // 新建组是全新对象 → 立刻重放残缺标记（同 Stage 2.5 的处理）。
            CollectionPipeline.MergeResult merge = CollectionPipeline.applyResultMerge(
                    list,
                    minecraft.level == null ? null : SlotDisplayContext.fromLevel(minecraft.level),
                    this::brbe$reapplyPartialMarking);
            list = merge.list();
            brbe$logMergeStats();

            // Stage 2.5: 排序原因剥离（用户 2026-09-27 诉求）——替代配方组里"需要调整排序"的
            // 变体（pin / 可合成 / 残缺 / 搜索命中）从原组剥出来、按类别各自成组，**多层递归**；
            // 原组只保留基线（最低类别）变体且位置不变。取代原 Stage 6 的 pin 专用剥离
            // （pin 行为不变：全 pin 原组保留、部分 pin 剥离后由 Stage 3 置顶）。
            // 新建的子组立刻重放残缺标记（标记按集合对象身份记录，见 brbe$reapplyPartialMarking）。
            // ⚠️「拆散替代配方组」= 关闭（OFF）时**不跑**本 stage（用户 2026-10-03 指令）：
            // "关闭"要连选择性拆散一起关掉 —— 否则组内变体仍会按 pin / 可合成 / 残缺 / 搜索命中
            // 被拆出去。此时替代配方组整体保持原样，组内变体在同一个按钮上轮循。
            if (BetterRecipeBook.config.alternativeRecipes.selectiveSplitEnabled()) {
                list = CollectionPipeline.applySortExtraction(
                        list, brbe$parsedQuery,
                        minecraft.level == null ? null : SlotDisplayContext.fromLevel(minecraft.level),
                        this::brbe$reapplyPartialMarking,
                        merge);
            }

            // Stage 3: Pins sort (in-place — moves pinned to front)
            CollectionPipeline.applyPins(list);

            // Stage 4: Craftable-before-partial sort (pin-aware).
            //
            // Two modes (spec §2.10):
            //   Default mode  (partialCraftingEnabled=false): filter button
            //     visible — sort only when isFiltering=true.
            //   Alternative   (partialCraftingEnabled=true):  filter button
            //     hidden  — always sort (craftable → partial → uncraftable).
            {
                boolean filterButtonHidden = BetterRecipeBook.config.partialCraftingEnabled;
                boolean shouldSort = filterButtonHidden || isFiltering;
                if (shouldSort) {
                    boolean hasPartialData = BetterRecipeBook.config.partialMarkingEnabled;
                    list = CollectionPipeline.applyPartialSort(list, true, hasPartialData);
                }
            }

            brbe$cachedPipelinedList = new ArrayList<>(list);
            // 指纹算的是**输入列表**（Stage 1 之前那次），命中时用新输入重算即可比对。
            brbe$cacheFingerprint = brbe$fingerprint;
            // 以下代理量仅**诊断用**（打印"为什么这一轮是 miss"），不再参与命中判定。
            brbe$cacheIndexVersion = com.alonie.brbe.util.RecipeCraftingIndex.currentVersion();
            brbe$cacheGeneration = com.alonie.brbe.util.RecipeCraftingIndex.generation();
            brbe$cachePinVersion = BetterRecipeBook.pinnedRecipeManager.version();
            brbe$cacheSearchText = brbe$currentSearchText();
            brbe$cacheIsFiltering = isFiltering;
            brbe$cacheConfigKey = brbe$configKey();
            brbe$cacheHasPipelined = true;
        }

        // Stage 6b（保留，改注释）：剥离已在 Stage 2.5 完成；这里每帧无条件重放一遍残缺标记——
        // 缓存命中时列表里的子组来自**快照**，而 PartialCraftingUtil.invalidateCaches()（配置
        // 变化等）会清掉标记，不清的话子组内的残缺配方会显示成不可合成。wasChecked 幂等，
        // 已标记过的集合直接跳过，代价可忽略。
        brbe$reapplyPartialMarking(list);

        page.updateCollections(list, resetPageNumber, isFiltering);
    }

    /**
     * **管线输出缓存的键**：把"上游纪元"与"输入数据实际内容"混成一个 int。
     *
     * <p>分量：{@link com.alonie.brbe.util.PipelineEpoch#current()}（生产者是否动过）、
     * pin 版本、配置键、搜索词、{@code isFiltering}、
     * {@link PartialCraftingUtil#pipelineStateHash(java.util.List)}（集合身份 + craftable/
     * selected + 残缺标记）。
     *
     * <p>为什么不是"能证明没变才失效"而是"直接哈希输入"：这是**纯性能**缓存，miss 的代价
     * 只是一次管线（本来也只在库存变化时跑），而假命中的代价是给玩家看错的东西 ——
     * 宁可多失效。
     */
    @Unique
    private int brbe$pipelineFingerprint(List<RecipeCollection> list, boolean isFiltering) {
        int h = com.alonie.brbe.util.PipelineEpoch.current();
        h = h * 31 + (BetterRecipeBook.pinnedRecipeManager == null
                ? 0 : BetterRecipeBook.pinnedRecipeManager.version());
        h = h * 31 + brbe$configMask();
        h = h * 31 + brbe$currentSearchText().hashCode();
        h = h * 31 + (isFiltering ? 1 : 0);
        h = h * 31 + PartialCraftingUtil.pipelineStateHash(list);
        return h;
    }

    /** Stage 1–4（搜索 → 展开 → pin 排序 → 残缺排序）。抽出来是为了让诊断自检能复用。 */
    @Unique
    private List<RecipeCollection> brbe$runStages(List<RecipeCollection> list, boolean isFiltering) {
        // Stage 1: Advanced search filter
        if (brbe$parsedQuery != null && minecraft.level != null) {
            list = CollectionPipeline.applySearch(
                    list, brbe$parsedQuery,
                    SlotDisplayContext.fromLevel(minecraft.level));
        }

        // Stage 2: Ungroup split (if splitMode == 完全)
        list = CollectionPipeline.applyUngroup(list);

        // Stage 2.6: 同产物合并（同上；诊断路径**不**重放残缺标记——那会改全局标记状态）
        list = CollectionPipeline.applyResultMerge(
                list,
                minecraft.level == null ? null : SlotDisplayContext.fromLevel(minecraft.level),
                null).list();

        // Stage 3: Pins sort (in-place — moves pinned to front)
        CollectionPipeline.applyPins(list);

        // Stage 4: Craftable-before-partial sort (pin-aware).
        //
        // Two modes (spec §2.10):
        //   Default mode  (partialCraftingEnabled=false): filter button
        //     visible — sort only when isFiltering=true.
        //   Alternative   (partialCraftingEnabled=true):  filter button
        //     hidden  — always sort (craftable → partial → uncraftable).
        {
            boolean filterButtonHidden = BetterRecipeBook.config.partialCraftingEnabled;
            boolean shouldSort = filterButtonHidden || isFiltering;
            if (shouldSort) {
                boolean hasPartialData = BetterRecipeBook.config.partialMarkingEnabled;
                list = CollectionPipeline.applyPartialSort(list, true, hasPartialData);
            }
        }
        return list;
    }

    /**
     * Stage 0 —— 网格可见性（显示路径兜底）。
     *
     * <p>2×2 合成网格 + {@code showAllRecipesInSurvival=OFF} 时，把"需要更大网格"的配方
     * 从 {@code selected} / {@code craftable} 中剔除，只含 3×3 的集合整组丢弃。vanilla
     * 的 {@code CraftingRecipeBookComponent.canDisplay} 本应如此计算，但增量 canCraft 索引
     * （{@link RecipeCraftingIndex}）会跳过不受库存变化影响的 {@code selectRecipes} ——
     * 一旦网格尺寸 / 界面 / 开关变化而签名没变，selected 就是上一轮的残留：3×3 配方留在
     * 2×2 背包配方书里（无不可合成标记、可点击 → 幽灵物品），或在游戏内关掉开关后不消失。
     * 这里挂在显示路径上，与 selected 是否重算无关，结果恒定正确；语义与 1.21.1 的
     * {@code RecipePipeline.applyVisibility} 一致。</p>
     */
    @Unique
    private List<RecipeCollection> brbe$applyGridVisibility(List<RecipeCollection> list) {
        if (BetterRecipeBook.config == null
                || BetterRecipeBook.config.showAllRecipesInSurvival) {
            return list;
        }
        int gridWidth = 0;
        int gridHeight = 0;
        if (this.menu instanceof net.minecraft.world.inventory.AbstractCraftingMenu craftingMenu) {
            gridWidth = craftingMenu.getGridWidth();
            gridHeight = craftingMenu.getGridHeight();
        }
        // 3×3 及以上网格：所有合成配方都放得下，无需过滤（非合成网格 w=h=0 也走这里，
        // 但那类配方书不是本 mixin 的 target）。
        if (gridWidth >= 3 && gridHeight >= 3) return list;

        List<RecipeCollection> out = new java.util.ArrayList<>(list.size());
        for (RecipeCollection collection : list) {
            RecipeCollectionAccessor accessor = (RecipeCollectionAccessor) collection;
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                if (!PartialCraftingUtil.needsLargerGrid(entry.display())) continue;
                RecipeDisplayId id = entry.id();
                accessor.brbe$getSelected().remove(id);
                accessor.brbe$getCraftable().remove(id);
            }
            if (collection.hasAnySelected()) {
                out.add(collection);
            }
        }
        return out;
    }

    /**
     * Stage 0b：进度可见性。{@code unlockAll} 关闭时，把不在
     * {@link com.alonie.brbe.util.ProgressionUnlocks} 白名单里的 display 从
     * {@code selected}/{@code craftable} 中剔除，只剩白名单项的集合整组丢弃。
     *
     * <p>挂在显示路径（指纹/缓存之前、每轮无条件执行）上，因此无论
     * {@code selectRecipes} 是否被增量索引跳过，显示结果都由进度白名单决定；
     * 白名单本身按脏标记缓存，只有世界加入/成就变化/开关变化后重算一次。</p>
     */
    @Unique
    private List<RecipeCollection> brbe$applyProgressionVisibility(List<RecipeCollection> list) {
        if (!com.alonie.brbe.util.ProgressionUnlocks.filtersActive()) {
            return list;
        }
        java.util.Set<RecipeDisplayId> allowed =
                com.alonie.brbe.util.ProgressionUnlocks.whitelist();
        if (allowed == null) {
            return list;
        }
        List<RecipeCollection> out = new java.util.ArrayList<>(list.size());
        int hidden = 0;
        for (RecipeCollection collection : list) {
            RecipeCollectionAccessor accessor = (RecipeCollectionAccessor) collection;
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                RecipeDisplayId id = entry.id();
                if (allowed.contains(id)) continue;
                accessor.brbe$getSelected().remove(id);
                accessor.brbe$getCraftable().remove(id);
                hidden++;
            }
            if (collection.hasAnySelected()) {
                out.add(collection);
            }
        }
        com.alonie.brbe.util.ProgressionUnlocks.noteHidden(hidden);
        return out;
    }

    /** [诊断] 命中时用**当前输入**重算 Stage 1–4，比较"来自输入列表的元素"的顺序。
     *
     *  <p>只比较输入来源的元素：Stage 2（展开分组）与 Stage 6（pin 重打包）生成的合成组
     *  每轮都是新对象、身份不稳定，跳过它们这个比较才是稳定的。
     *  真一致 → 缓存安全；不一致 → 有输入没进指纹，打 ERROR 并列出各分量。
     *  诊断自身抛异常绝不影响渲染。 */
    @Unique
    private void brbe$verifyCacheHit(List<RecipeCollection> input,
                                     List<RecipeCollection> cached, int fingerprint) {
        try {
            java.util.Set<RecipeCollection> inputSet =
                    java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            inputSet.addAll(input);
            List<RecipeCollection> fresh = brbe$runStages(new ArrayList<>(input), brbe$cacheIsFiltering);
            String expect = brbe$inputOriginSequence(cached, inputSet);
            String actual = brbe$inputOriginSequence(fresh, inputSet);
            if (!expect.equals(actual)) {
                BetterRecipeBook.LOGGER.error("[BRBE-CACHE] 过期命中（有输入没进指纹）fingerprint={}"
                                + " pins={} cfg={} search='{}' filtering={} epoch={} stateHash={}"
                                + "\n  缓存顺序={}\n  实测顺序={}",
                        fingerprint, brbe$cachePinVersion, brbe$cacheConfigKey, brbe$cacheSearchText,
                        brbe$cacheIsFiltering, com.alonie.brbe.util.PipelineEpoch.current(),
                        PartialCraftingUtil.pipelineStateHash(input), expect, actual);
            }
        } catch (Throwable ignored) {
            // 诊断失败绝不影响渲染
        }
    }

    /** [诊断] 列表里"来自输入列表"的元素的身份序列（合成组跳过）。 */
    @Unique
    private static String brbe$inputOriginSequence(List<RecipeCollection> list,
                                                   java.util.Set<RecipeCollection> inputSet) {
        StringBuilder sb = new StringBuilder("[");
        for (RecipeCollection c : list) {
            if (!inputSet.contains(c)) continue;
            if (sb.length() > 1) sb.append(',');
            sb.append(System.identityHashCode(c));
        }
        return sb.append(']').toString();
    }

    /**
     * 对最终管线列表重放残缺标记（Stage 6b，见调用处注释）。
     * 计算参数与 incompletecrafting/RecipeBookComponentMixin 主 passes 完全一致；
     * markPartialMaterials 内部以 wasChecked 幂等——原组跳过，新组重新标记；
     * 标记后按主 passes 顺序：carried/offhand 提升 → 残缺 ID 注入 craftable 集合
     * （注入后 isCraftable() 为 true，红罩渲染依赖它 + tagger 标记）。
     */
    @Unique
    private void brbe$reapplyPartialMarking(List<RecipeCollection> collections) {
        if (collections == null || collections.isEmpty()) return;
        boolean onInventoryScreen = this.minecraft != null
                && this.minecraft.gui.screen() instanceof InventoryScreen;
        ItemStack carried = this.menu != null
                ? this.menu.getCarried() : ItemStack.EMPTY;
        Set<Item> inventoryItems = PartialCraftingUtil.hashInventory(
                PartialCraftingUtil.searchSpaceSlots(), -1, carried);
        // Item → 总数量（数量感知的材料齐全判定，与主 passes 同源）。
        Map<Item, Integer> inventoryCounts = new HashMap<>();
        for (net.minecraft.world.inventory.Slot slot : PartialCraftingUtil.searchSpaceSlots()) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                inventoryCounts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        if (!carried.isEmpty()) {
            inventoryCounts.merge(carried.getItem(), carried.getCount(), Integer::sum);
        }
        ItemStack offhand = PartialCraftingUtil.offhandStack();
        if (!offhand.isEmpty()) {
            inventoryCounts.merge(offhand.getItem(), offhand.getCount(), Integer::sum);
        }
        boolean partialOnlyWhenCarrying = BetterRecipeBook.config.partialOnlyWhenCarrying;
        Set<Item> markItems = partialOnlyWhenCarrying
                ? (carried.isEmpty() ? Set.of() : Set.of(carried.getItem()))
                : inventoryItems;

        for (RecipeCollection collection : collections) {
            PartialCraftingUtil.markPartialMaterials(
                    collection, inventoryItems, inventoryCounts, markItems, onInventoryScreen);
            if (!carried.isEmpty() || !offhand.isEmpty()) {
                PartialCraftingUtil.elevateFullyCraftableWithCarried(
                        collection, inventoryItems, inventoryCounts, onInventoryScreen);
            }
            if (onInventoryScreen && BetterRecipeBook.config.showAllRecipesInSurvival) {
                PartialCraftingUtil.elevateFullyCraftable3x3(
                        collection, inventoryItems, inventoryCounts);
            }
            if (PartialCraftingUtil.hasPartialMaterials(collection)) {
                RecipeCollectionAccessor accessor = (RecipeCollectionAccessor) collection;
                for (RecipeDisplayEntry entry : collection.getRecipes()) {
                    RecipeDisplayId id = entry.id();
                    if (PartialCraftingUtil.isPartiallyCraftable(collection, id)) {
                        accessor.brbe$getCraftable().add(id);
                    }
                }
            }
        }
    }

    @Unique
    private boolean configKey() {
        if (BetterRecipeBook.config == null) return false;
        return BetterRecipeBook.config.partialCraftingEnabled
                || BetterRecipeBook.config.partialMarkingEnabled
                || BetterRecipeBook.config.alternativeRecipes.noGrouped();
    }

    /**
     * **合并诊断行**（{@code logs/brbe-debug.log} 的 {@code [BRBE-MERGE]}）：开关开启时，
     * 每次合并摘要变化就打一行 —— "建了几格、融了几条、哪些产物被哪条判据挡下"。
     *
     * <p>为什么需要它：2026-09-29 用户实测"看不到任何融合、收纳格还被拆散"，而当时无法从
     * 任何证据区分三种完全不同的原因（开关没开 / 没建格 / 全被判据挡下）。这一行让下一次
     * 测试自带答案。开关关闭时**不打**（保持日志干净），此时"有没有这一行"本身就是答案。</p>
     */
    @Unique
    private void brbe$logMergeStats() {
        if (BetterRecipeBook.config == null
                || !BetterRecipeBook.config.alternativeRecipes.mergeSameResult) {
            return;
        }
        String summary = CollectionPipeline.mergeDiagnostics();
        String key = brbe$configKey() + "|" + summary;
        if (key.equals(brbe$lastMergeLog)) {
            return;
        }
        brbe$lastMergeLog = key;
        com.alonie.brbe.util.BrbeLogger.log("BRBE-MERGE",
                "{} splitMode={} {}", bookKey(),
                BetterRecipeBook.config.alternativeRecipes.splitMode, summary);
    }

    /**
     * 融合条目点击时按物品栏挑版本（用户 2026-09-29 定）。
     *
     * <p>融合条目展示的是逐槽选项的**并集**（差异部分轮循），但服务端是按 {@link RecipeDisplayId}
     * 找**真实配方**的：只把主成员的 id 发过去，玩家手上只有另一版材料时会放不出来。所以在发包前
     * 换成"成员里当前物品栏真能做的那一条"（{@code RecipeDisplayEntry#canCraft(StackedItemContents)}
     * 用的就是成员各自真实的 requirements）；都做不了就用主成员 id 走原版行为（部分放置/幽灵预览）。</p>
     */
    @Redirect(method = "tryPlaceRecipe",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;handlePlaceRecipe(ILnet/minecraft/world/item/crafting/display/RecipeDisplayId;Z)V"))
    private void brbe$placeFusedVariant(MultiPlayerGameMode gameMode, int containerId,
                                        RecipeDisplayId id, boolean useMaxItems) {
        gameMode.handlePlaceRecipe(containerId,
                FusedRecipeVariants.bestVariant(id, this.stackedContents), useMaxItems);
    }

    @Unique
    private String bookKey() {
        String type = menu != null ? menu.getRecipeBookType().name() : "";
        String screen = menu != null ? menu.getClass().getSimpleName() : "";
        return "vanilla:" + type + ":" + screen;
    }
}

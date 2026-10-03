package com.alonie.recipebookispain_extended.mixin.widget;

import com.alonie.recipebookispain_extended.RecipeBookIsPain;
import com.alonie.recipebookispain_extended.RecipeBookIsPain.FurnaceVariant;
import com.alonie.recipebookispain_extended.RecipeBookIsPainExtendedConfig;
import com.alonie.recipebookispain_extended.compat.polymer.PolymerCompat;
import com.alonie.recipebookispain_extended.access.RecipeGroupButtonFlipAccess;
import com.alonie.recipebookispain_extended.access.RecipeGroupButtonPlacement;
import com.alonie.recipebookispain_extended.access.RecipeGroupButtonPlacementAccess;
import com.alonie.recipebookispain_extended.access.RecipeBookScrollAccess;
import com.alonie.recipebookispain_extended.animation.TabFlipGeometry;
import com.alonie.recipebookispain_extended.animation.TabFlipState;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.screens.recipebook.CraftingRecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.FurnaceRecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.gui.screens.recipebook.SearchRecipeBookCategory;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(RecipeBookComponent.class)
public class RecipeBookWidgetMixin implements RecipeBookScrollAccess {
    @Unique private static final Identifier RBIP_PAGE_BUTTONS = Identifier.fromNamespaceAndPath("brbe", "textures/rbip/recipe_book_buttons.png");
    @Unique private static final int RBIP_FALLBACK_GROUPS_PER_PAGE = 5;
    @Unique private static final int RBIP_PAGE_BUTTON_WIDTH = 14;
    @Unique private static final int RBIP_PAGE_BUTTON_HEIGHT = 13;
    @Unique private static final int RBIP_BOOK_WIDTH = 147;
    @Unique private static final int RBIP_BOOK_HEIGHT = 166;
    @Unique private static final int RBIP_TAB_WIDTH = 35;
    @Unique private static final int RBIP_TAB_HEIGHT = 27;
    @Unique private static final int RBIP_ROTATED_TAB_WIDTH = 27;
    @Unique private static final int RBIP_ROTATED_TAB_HEIGHT = 35;
    @Unique private static final int RBIP_LEFT_TOTAL_SLOTS = 6;
    @Unique private static final int RBIP_BOTTOM_SLOTS = 5;
    @Unique private static final int RBIP_TOP_SLOTS = 5;
    @Unique private static final int RBIP_EXTENDED_SLOT_STEP = 27;
    @Unique private static final int RBIP_HORIZONTAL_SCROLL_OUTWARD_PADDING = 20;
    /** 标签栏翻页动画与配方区翻页动画共用同一条指数减速曲线：rate = 6.2 / 时长（秒）。 */
    @Unique private static final float RBIP_TAB_FLIP_BASE_RATE = 6.2F;
    /** 单帧位移比例上限（与配方区动画同一公式）：0.45 + sqrt(剩余量) * 0.12。 */
    @Unique private static final float RBIP_TAB_FLIP_CAP_BASE = 0.45F;
    @Unique private static final float RBIP_TAB_FLIP_CAP_SCALE = 0.12F;
    /** 收敛阈值：所有标签的剩余位移都小于它即收尾（与配方区动画一致）。 */
    @Unique private static final float RBIP_TAB_FLIP_SNAP = 0.002F;
    /**
     * 「视觉落地」阈值：{@link TabFlipGeometry#RETRACT_DISTANCE} 像素里差半个像素以内时，
     * 位移取整后与静止位**逐像素一致**（画面上已经完全停住），此时就该收尾、开始落地渐显。
     *
     * <p>若沿用 {@link #RBIP_TAB_FLIP_SNAP}（0.002 ≈ 0.07px）收尾，画面停住之后指数尾巴还要再跑
     * 3~4 帧（≈0.06s）才算结束 —— 用户看到的就是"停下来之后愣一下才开始过渡"
     * （2026-10-04 反馈"过渡动画应该发生在停下来的一瞬间"）。</p>
     */
    @Unique private static final float RBIP_TAB_FLIP_LAND = 0.5F / TabFlipGeometry.RETRACT_DISTANCE;

    @Shadow @Final @Mutable private List<RecipeBookComponent.TabInfo> tabInfos;
    @Shadow @Final private List<RecipeBookTabButton> tabButtons;
    @Shadow protected Minecraft minecraft;
    @Shadow private ClientRecipeBook book;
    @Shadow private int width;
    @Shadow private int height;
    @Shadow private int xOffset;
    @Shadow private RecipeBookTabButton selectedTab;
    @Shadow
    public boolean isVisible() {
        throw new AssertionError();
    }

    @Shadow
    public void updateTabs(boolean filteringCraftable) {
        throw new AssertionError();
    }

    @Shadow
    public void recipesUpdated() {
        throw new AssertionError();
    }

    @Unique private List<RecipeBookComponent.TabInfo> rbip$vanillaTabInfos;

    @Unique private RecipeBookTabButton rbip$pinnedTab;
    @Unique private List<RecipeBookTabButton> rbip$pageableTabs = List.of();
    @Unique private int rbip$page;
    @Unique private int rbip$pageCount;
    @Unique private int rbip$pageControlX;
    @Unique private int rbip$pageControlY;
    /** 上一次为"当前标签栏还没集合"强制重建集合的时间（500ms 去抖，见
     *  {@code rbip$paginateTabButtons}）。 */
    @Unique private long rbip$lastCollectionsRefreshAt;
    /** 上次 {@code init} 时的「标签布局」指纹（见 {@code RecipeBookIsPainExtendedConfig#tabLayoutKey}）。 */
    @Unique private String rbip$tabLayoutKey;

    // ── 标签栏翻页动画 ────────────────────────────────────────────
    /** 标签栏翻页动画是否正在跑（退场标签在缩进 / 进场标签在伸出）。 */
    @Unique private boolean rbip$tabFlipActive;
    /** 翻页期间整块裁剪框（按静止位算）：标签在平移，位置不是静止位。 */
    @Unique private final int[] rbip$tabFlipClip = new int[4];
    /** 本次翻页是否来自用户操作（页控件点击 / 滚轮）——只有用户翻页才播动画。 */
    @Unique private boolean rbip$tabFlipUserFlip;
    /** 用户翻页的起始页（调用方改 {@code rbip$page} 之前记下；重排内部读不到旧值）。 */
    @Unique private int rbip$tabFlipFromPage;
    /** 本轮动画累计的翻页跨度（页数）：连滚越大越快（见 {@link #rbip$tabFlipRate()}）。 */
    @Unique private float rbip$tabFlipSpan;
    /** 裁剪框暂存（每帧每标签一次，避免反复分配）。 */

    @Inject(at = @At("TAIL"), method = "<init>")
    private void rbip$addCreativeTabs(RecipeBookMenu handler, List<RecipeBookComponent.TabInfo> tabInfos, CallbackInfo ci) {
        this.rbip$vanillaTabInfos = List.copyOf(this.tabInfos);
        this.rbip$tabLayoutKey = RecipeBookIsPainExtendedConfig.tabLayoutKey();

        if (!RecipeBookIsPainExtendedConfig.enabled()) return;
        if ((Object) this instanceof CraftingRecipeBookComponent) {
            // 三档：创造模式物品栏 / 命名空间（每个命名空间一个标签）/ 数据包（每个来源包一个标签）。
            this.tabInfos = RecipeBookIsPain.craftingTabsForCurrentMode(this.rbip$vanillaTabInfos, tabInfos);
        } else if ((Object) this instanceof FurnaceRecipeBookComponent) {
            FurnaceVariant type = RecipeBookIsPain.detectFurnaceType(tabInfos);
            this.tabInfos = RecipeBookIsPain.furnaceTabsForCurrentMode(this.rbip$vanillaTabInfos, tabInfos, type);
        }
    }

    @Inject(at = @At("HEAD"), method = "updateTabs")
    private void rbip$syncLateGroups(CallbackInfo ci) {
        if (!RecipeBookIsPainExtendedConfig.enabled()) return;
        if ((Object) this instanceof CraftingRecipeBookComponent) {
            // PolymerCompat.refresh() = 重建命名空间缓存 + 遍历**全部注册物品**重新指派
            // 创造标签 + 重新登记所有创造标签，只为补 Polymer 把服务端自定义标签异步同步
            // 过来时的时序差（见 PolymerCompat 类注释）。没装 Polymer 时这一步纯属浪费：
            // updateTabs 在禁用配方书时曾被每 tick 触发一次（见 DisableBook 注释），每次都要
            // 扫一遍物品注册表并打一条 [RBIP] Namespace override 日志。
            if (RecipeBookIsPain.PLATFORM != null && RecipeBookIsPain.PLATFORM.isModLoaded("polymer")) {
                PolymerCompat.refresh();
            }
            this.tabInfos = RecipeBookIsPain.craftingTabsForCurrentMode(this.rbip$vanillaTabInfos, this.tabInfos);
        } else if ((Object) this instanceof FurnaceRecipeBookComponent) {
            FurnaceVariant type = RecipeBookIsPain.detectFurnaceType(this.rbip$vanillaTabInfos);
            this.tabInfos = RecipeBookIsPain.furnaceTabsForCurrentMode(this.rbip$vanillaTabInfos, this.tabInfos, type);
        }
    }

    @Inject(at = @At("TAIL"), method = "updateTabs")
    private void rbip$paginateTabButtons(boolean filteringCraftable, CallbackInfo ci) {
        // ★ 标签"集体消失"的根因就在下面这段"空集合就隐藏"里（用户 2026-10-02 再次反馈：
        //   进存档后第一次打开配方书 / 刚切过标签档位时，除搜索标签外整排消失）。
        //   标签其实都建出来了 —— 消失是因为此刻 `collectionsByTab` 还是「RBIP 之前 /
        //   上一档」的版本（首次打开时 RBIP 的集合重建还没跑过；切档时新档的分组对象
        //   更是刚被 prewarm 建出来，集合一个都还没有），于是每个扩展标签都取不到集合、
        //   被判成空整排隐藏。修法：隐藏之前先确认"集合与当前标签栏同代" —— 只要有非搜索
        //   标签取不到集合就先强制重建一次集合（500ms 去抖；正常状态不会触发，因为重建后
        //   每个真有配方的标签都有集合）。
        boolean keepTabsOnMismatch = false;
        if (RecipeBookIsPainExtendedConfig.enabled()) {
            int without = rbip$countTabsWithoutCollection();
            if (without > 0) {
                long now = System.currentTimeMillis();
                if (now - rbip$lastCollectionsRefreshAt >= 500L) {
                    rbip$lastCollectionsRefreshAt = now;
                    this.book.rebuildCollections();
                    int still = rbip$countTabsWithoutCollection();
                    com.alonie.brbe.util.BrbeLogger.log("RBIP",
                            "updateTabs: stale collections for the current tab set "
                                    + "({}/{} tabs without) → rebuilt collections, still without = {}",
                            without, rbip$countNonSearchTabs(), still);
                    without = still;
                }
                // 重建之后**每一个**非搜索标签仍取不到集合 = 集合表与标签不是同一套
                // （数据管线不同步）。这时隐藏会把整排标签抹掉，宁可保留标签（页面暂空，
                // getCollection 对未知分类返回空列表，不会崩），也不要"标签集体消失"。
                if (without > 0 && without >= rbip$countNonSearchTabs()) keepTabsOnMismatch = true;
            }
        }

        List<RecipeBookTabButton> pageableTabs = new ArrayList<>();
        RecipeBookTabButton pinnedTab = null;

        for (RecipeBookTabButton widget : this.tabButtons) {
            if (!widget.visible) continue;

            // Highest priority: hide tabs whose category has no recipe collections.
            // This always applies regardless of any feature toggle state.
            List<RecipeCollection> collections = this.book.getCollection(widget.getCategory());
            if (!keepTabsOnMismatch && (collections == null || collections.isEmpty())) {
                widget.visible = false;
                continue;
            }

            if (pinnedTab == null && widget.getCategory() instanceof SearchRecipeBookCategory) {
                pinnedTab = widget;
            } else {
                pageableTabs.add(widget);
            }
        }

        this.rbip$pinnedTab = pinnedTab;
        this.rbip$pageableTabs = pageableTabs;
        this.rbip$applyPagination(true);
    }

    /** 当前标签栏里**非搜索**标签中"取不到集合"的个数 —— 只有"集合还没按当前档位重建"
     *  时才大于 0（正常状态每个真有配方的标签都有集合）。判定依据与下面的隐藏逻辑完全一致。 */
    @Unique
    private int rbip$countTabsWithoutCollection() {
        int count = 0;
        for (RecipeBookTabButton widget : this.tabButtons) {
            if (!widget.visible) continue;
            if (widget.getCategory() instanceof SearchRecipeBookCategory) continue;
            List<RecipeCollection> collections = this.book.getCollection(widget.getCategory());
            if (collections == null || collections.isEmpty()) count++;
        }
        return count;
    }

    /** 当前标签栏里**非搜索**标签的总数（日志与"全空"判定用）。 */
    @Unique
    private int rbip$countNonSearchTabs() {
        int count = 0;
        for (RecipeBookTabButton widget : this.tabButtons) {
            if (!widget.visible) continue;
            if (widget.getCategory() instanceof SearchRecipeBookCategory) continue;
            count++;
        }
        return count;
    }

    /**
     * **配置界面改过档位 / 开关后，在 vanilla {@code initVisuals()} 造标签按钮之前**把
     * {@code tabInfos} 与集合换成新档位的版本（用户 2026-10-03 反馈：在配方书里切档后
     * 除搜索标签外整排消失）。
     *
     * <p>为什么必须挂在 {@code init} 的 HEAD：vanilla {@code initVisuals()} 的顺序是
     * <b>按当前 {@code tabInfos} 现造 {@code RecipeBookTabButton}</b> →
     * {@code selectMatchingRecipes()}（遍历 tabInfos 把每个集合"选中"）→ {@code updateTabs()}
     * → {@code updateCollections()}；而 {@code updateTabs()} 只重排**已有**按钮、不会为新分类
     * 造按钮。晚一步（例如只在 {@code updateTabs} 的 HEAD 重建 tabInfos）就会变成
     * "新档位的分类一个按钮都没有" → 只剩搜索标签。</p>
     *
     * <p>顺带重建集合：{@code collectionsByTab} 也还是旧档位的，而 {@code updateTabs()} 里
     * {@code RecipeBookTabButton.updateVisibility()} 要求集合 {@code hasAnySelected()}
     * —— 只要集合是最新重建的、且顺序正确（先 selectMatchingRecipes 再 updateTabs），
     * 标签才可见。</p>
     */
    /** 重新布局（打开配方书 / 改窗口尺寸）会整批重建标签按钮：动画状态一并清零。 */
    @Inject(at = @At("HEAD"), method = "init")
    private void rbip$resetTabFlipOnInit(int width, int height, Minecraft minecraft, boolean widthTooNarrow,
                                         CallbackInfo ci) {
        this.rbip$endTabFlip();
    }

    @Inject(at = @At("HEAD"), method = "init")
    private void rbip$rebuildTabsOnInit(int width, int height, Minecraft minecraft, boolean widthTooNarrow,
                                        CallbackInfo ci) {
        if (this.rbip$vanillaTabInfos == null) return;
        if (!((Object) this instanceof CraftingRecipeBookComponent)
                && !((Object) this instanceof FurnaceRecipeBookComponent)) return;
        String key = RecipeBookIsPainExtendedConfig.tabLayoutKey();
        if (key.equals(this.rbip$tabLayoutKey)) return;
        this.rbip$tabLayoutKey = key;
        rbip$rebuildTabInfosForCurrentMode();
        this.book.rebuildCollections();
    }

    /** 按**当前**档位 / 开关重建 {@code tabInfos}（与 {@code rbip$syncLateGroups} 同一套分派）。 */
    @Unique
    private void rbip$rebuildTabInfosForCurrentMode() {
        if (!RecipeBookIsPainExtendedConfig.enabled()) {
            this.tabInfos = new ArrayList<>(this.rbip$vanillaTabInfos);
            return;
        }
        if ((Object) this instanceof CraftingRecipeBookComponent) {
            this.tabInfos = RecipeBookIsPain.craftingTabsForCurrentMode(this.rbip$vanillaTabInfos, this.tabInfos);
        } else {
            this.tabInfos = RecipeBookIsPain.furnaceTabsForCurrentMode(this.rbip$vanillaTabInfos, this.tabInfos,
                    RecipeBookIsPain.detectFurnaceType(this.rbip$vanillaTabInfos));
        }
    }

    @Inject(at = @At("HEAD"), method = "extractRenderState")
    private void rbip$hotReloadOnConfigChange(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!RecipeBookIsPainExtendedConfig.reloadIfChanged()) return;
        if (this.rbip$vanillaTabInfos == null) return;
        if (!((Object) this instanceof CraftingRecipeBookComponent)
                && !((Object) this instanceof FurnaceRecipeBookComponent)) return;

        // tabInfos 与集合都还是旧档位/旧开关建的 → 先按新配置重建两者。
        rbip$rebuildTabInfosForCurrentMode();
        this.book.rebuildCollections();
        // ⚠️ 顺序要紧：刚重建的集合 `selected` 还是空的，而 vanilla `updateTabs()` 里
        //    `RecipeBookTabButton.updateVisibility()` 要求集合 `hasAnySelected()`
        //    —— 只 rebuild + updateTabs 会把除搜索标签（vanilla 恒可见）以外的标签整排隐藏
        //    （用户 2026-10-03 反馈的切档 bug）。所以走 vanilla 的完整顺序：
        //    `recipesUpdated()` = `selectMatchingRecipes()`（遍历 tabInfos 全量重选）
        //    → `updateTabs()` → `updateCollections()`。
        this.recipesUpdated();
    }

    /**
     * 标签栏翻页动画的推进（每帧一次，与配方区翻页动画同一条指数减速曲线）：
     * 每个参与标签的「伸出度」朝各自目标逼近 —— 落后越多走得越快，最后一页自然减速。
     */
    @Inject(at = @At("HEAD"), method = "extractRenderState")
    private void rbip$advanceTabFlip(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!this.rbip$tabFlipActive) return;
        if (!rbip$tabFlipEnabled()) {
            rbip$endTabFlip();
            return;
        }
        float step = 1.0F - (float) Math.exp(-rbip$tabFlipRate() * (delta / 20.0F));
        float remaining = 0.0F;
        for (RecipeBookTabButton widget : this.tabButtons) {
            TabFlipState state = ((RecipeGroupButtonFlipAccess) widget).rbip$flipState();
            if (!state.tracked) continue;
            float diff = state.target - state.extend;
            if (Math.abs(diff) < RBIP_TAB_FLIP_LAND) {
                // 半个像素内 → 画面已静止，直接吸附到端点（shift 取整后与静止位一致，无跳变）
                state.extend = state.target;
                continue;
            }
            float cap = RBIP_TAB_FLIP_CAP_BASE + (float) Math.sqrt(Math.abs(diff)) * RBIP_TAB_FLIP_CAP_SCALE;
            float move = Math.min(Math.min(Math.abs(diff) * step, cap), Math.abs(diff));
            state.extend += Math.signum(diff) * move;
            remaining = Math.max(remaining, Math.abs(state.target - state.extend));
        }
        if (remaining < RBIP_TAB_FLIP_SNAP) {
            rbip$endTabFlip();
        }
    }

    /**
     * 参与翻页动画的标签一律推迟到整块渲染之后统一绘制 —— 只有这样才能保证
     * <b>进场标签恒在退场标签之上</b>（原版循环是按标签自身的顺序逐个画的，
     * 退场/进场在列表里交错，就地画无法定序）。搜索标签与未参与动画的标签照旧。
     */
    @Redirect(method = "extractRenderState",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookTabButton;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void rbip$deferFlippingTabs(RecipeBookTabButton widget, GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (this.rbip$tabFlipActive && ((RecipeGroupButtonFlipAccess) widget).rbip$flipState().tracked) {
            return;
        }
        widget.extractRenderState(context, mouseX, mouseY, delta);
    }

    /** 翻页动画的绘制趟：先全部退场（target=0），再全部进场（target=1）。 */
    @Inject(at = @At("TAIL"), method = "extractRenderState")
    private void rbip$renderFlippingTabs(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!this.rbip$tabFlipActive) return;
        for (int pass = 0; pass < 2; pass++) {
            boolean incoming = pass == 1;
            for (RecipeBookTabButton widget : this.tabButtons) {
                TabFlipState state = ((RecipeGroupButtonFlipAccess) widget).rbip$flipState();
                if (!state.tracked || (state.target > 0.5F) != incoming) continue;
                rbip$drawFlippingTab(context, widget, state, mouseX, mouseY, delta);
            }
        }
    }

    @Inject(at = @At("TAIL"), method = "extractRenderState")
    private void rbip$renderPageControls(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!RecipeBookIsPainExtendedConfig.enabled()) return;
        // 「隐藏翻页按钮」：整块页控件不画 —— 两个箭头与页码提示一起消失。
        if (rbip$pageButtonsHidden()) return;
        if (!this.isVisible() || this.rbip$pageCount <= 1) return;

        boolean wrap = com.alonie.brbe.BetterRecipeBook.config.scrolling.scrollAround;
        this.rbip$drawPageControl(context, this.rbip$pageControlX, this.rbip$pageControlY, false, wrap || this.rbip$page > 0, mouseX, mouseY);
        this.rbip$drawPageControl(context, this.rbip$pageControlX + 15, this.rbip$pageControlY, true, wrap || this.rbip$page < this.rbip$pageCount - 1, mouseX, mouseY);

        if (this.minecraft.gui.screen() != null
                && (this.rbip$isInside(mouseX, mouseY, this.rbip$pageControlX, this.rbip$pageControlY, RBIP_PAGE_BUTTON_WIDTH, RBIP_PAGE_BUTTON_HEIGHT)
                || this.rbip$isInside(mouseX, mouseY, this.rbip$pageControlX + 15, this.rbip$pageControlY, RBIP_PAGE_BUTTON_WIDTH, RBIP_PAGE_BUTTON_HEIGHT))) {
            context.setTooltipForNextFrame(this.minecraft.font, Component.literal((this.rbip$page + 1) + "/" + this.rbip$pageCount), mouseX, mouseY);
        }
    }

    @Inject(at = @At("HEAD"), method = "mouseClicked", cancellable = true)
    private void rbip$mouseClickedPageControls(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (!RecipeBookIsPainExtendedConfig.enabled()) return;
        // 按钮已隐藏：该区域不再吞掉点击，交回配方书原本的点击逻辑。
        if (rbip$pageButtonsHidden()) return;
        if (!this.isVisible() || this.rbip$pageCount <= 1 || !com.alonie.brbe.util.ClientCompat.isLeftClick(click)) return;

        int x = (int) click.x();
        int y = (int) click.y();
        boolean wrap = com.alonie.brbe.BetterRecipeBook.config.scrolling.scrollAround;
        // Ctrl+左键：直接跳转首页/尾页（与配方区翻页箭头一致）。
        boolean ctrl = com.alonie.brbe.util.ClientCompat.isControlDown();

        if (this.rbip$isInside(x, y, this.rbip$pageControlX, this.rbip$pageControlY, RBIP_PAGE_BUTTON_WIDTH, RBIP_PAGE_BUTTON_HEIGHT)) {
            int prev = ctrl ? 0
                    : wrap
                            ? (this.rbip$page - 1 + this.rbip$pageCount) % this.rbip$pageCount
                            : Math.max(0, this.rbip$page - 1);
            if (prev != this.rbip$page) {
                this.rbip$tabFlipFromPage = this.rbip$page;
                this.rbip$page = prev;
                this.rbip$tabFlipUserFlip = true;
                this.rbip$applyPagination(false);
                com.alonie.brbe.util.ClientCompat.playPageFlipSound(this.minecraft);
                cir.setReturnValue(true);
            }
        } else if (this.rbip$isInside(x, y, this.rbip$pageControlX + 15, this.rbip$pageControlY, RBIP_PAGE_BUTTON_WIDTH, RBIP_PAGE_BUTTON_HEIGHT)) {
            int next = ctrl ? this.rbip$pageCount - 1
                    : wrap
                            ? (this.rbip$page + 1) % this.rbip$pageCount
                            : Math.min(this.rbip$pageCount - 1, this.rbip$page + 1);
            if (next != this.rbip$page) {
                this.rbip$tabFlipFromPage = this.rbip$page;
                this.rbip$page = next;
                this.rbip$tabFlipUserFlip = true;
                this.rbip$applyPagination(false);
                com.alonie.brbe.util.ClientCompat.playPageFlipSound(this.minecraft);
                cir.setReturnValue(true);
            }
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return this.rbip$scrollPages(mouseX, mouseY, verticalAmount);
    }

    @Override
    public boolean rbip$scrollPages(double mouseX, double mouseY, double verticalAmount) {
        // 桌面窗口语义：光标落在查询界面/pin/预览（query UI 拥有的点）上时，
        // RBIP 标签条不得透过它们滚动（MouseHandler 层的 onScroll 注入会在
        // 屏幕分发前拦截，必须在这里挡下）；查询界面之外照常滚动。
        if (com.alonie.brbe.util.RecipeViewerOverlay.modalMaskOwnsCursor((int) Math.floor(mouseX), (int) Math.floor(mouseY))) {
            return false;
        }
        if (!RecipeBookIsPainExtendedConfig.get().extendedFeatures()
                || this.rbip$pageCount <= 1
                || verticalAmount == 0.0D
                || !this.rbip$isMouseOverAnyVisibleTab(mouseX, mouseY)) {
            return false;
        }

        int nextPage = this.rbip$page + (verticalAmount > 0.0D ? -1 : 1);
        if (com.alonie.brbe.BetterRecipeBook.config.scrolling.scrollAround) {
            // Wrap around: a scroll past the last page returns to the first
            // (and past the first goes to the last), matching scrollAround.
            nextPage = (nextPage % this.rbip$pageCount + this.rbip$pageCount) % this.rbip$pageCount;
        } else {
            nextPage = Math.max(0, Math.min(nextPage, this.rbip$pageCount - 1));
        }
        if (nextPage != this.rbip$page) {
            this.rbip$tabFlipFromPage = this.rbip$page;
            this.rbip$page = nextPage;
            this.rbip$tabFlipUserFlip = true;
            this.rbip$applyPagination(false);
            com.alonie.brbe.util.ClientCompat.playPageFlipSound(this.minecraft);
        }
        return true;
    }

    @Override
    public int rbip$getPage() {
        return this.rbip$page;
    }

    @Override
    public void rbip$setPage(int page) {
        this.rbip$page = page;
        this.rbip$applyPagination(false);
    }

    @Unique
    private void rbip$applyPagination(boolean followCurrentTab) {
        int slot = 0;
        int groupsPerPage = this.rbip$getGroupsPerPage();

        // 翻页动画：先把「翻页前这一页」的标签记成退场基准 —— 下面的重排会改写它们的
        // 朝向与坐标（离场标签还会被 resetTabPlacement 打回 NORMAL），晚一步就取不到了。
        if (this.rbip$tabFlipUserFlip) {
            rbip$captureFlipOutgoing();
        }

        if (this.rbip$pinnedTab != null) {
            this.rbip$pinnedTab.visible = true;
            this.rbip$placeTab(this.rbip$pinnedTab, slot);
            slot++;
        }

        this.rbip$pageCount = (this.rbip$pageableTabs.size() + groupsPerPage - 1) / groupsPerPage;
        if (this.rbip$pageCount <= 1) {
            this.rbip$page = 0;
            this.rbip$pageControlX = this.rbip$getPageControlX();
            this.rbip$pageControlY = this.rbip$getPageControlY();
            for (RecipeBookTabButton widget : this.rbip$pageableTabs) {
                widget.visible = true;
                this.rbip$placeTab(widget, slot++);
            }
            rbip$finishPagination();
            return;
        }

        if (followCurrentTab && this.selectedTab != null) {
            int currentIndex = this.rbip$pageableTabs.indexOf(this.selectedTab);
            if (currentIndex >= 0) {
                this.rbip$page = currentIndex / groupsPerPage;
            }
        }

        this.rbip$page = Math.max(0, Math.min(this.rbip$page, this.rbip$pageCount - 1));
        int start = this.rbip$page * groupsPerPage;
        int end = Math.min(start + groupsPerPage, this.rbip$pageableTabs.size());

        for (int i = 0; i < this.rbip$pageableTabs.size(); i++) {
            RecipeBookTabButton widget = this.rbip$pageableTabs.get(i);
            boolean onPage = i >= start && i < end;
            widget.visible = onPage;
            if (onPage) {
                this.rbip$placeTab(widget, slot++);
            } else {
                this.rbip$resetTabPlacement(widget);
            }
        }

        this.rbip$pageControlX = this.rbip$getPageControlX();
        this.rbip$pageControlY = this.rbip$getPageControlY();
        rbip$finishPagination();
    }

    @Unique
    private int rbip$getGroupsPerPage() {
        RecipeBookIsPainExtendedConfig config = RecipeBookIsPainExtendedConfig.get();
        if (!config.extendedFeatures()) {
            return RBIP_FALLBACK_GROUPS_PER_PAGE;
        }

        int pinnedCount = this.rbip$pinnedTab == null ? 0 : 1;
        return Math.max(1, config.bottomNumber() - pinnedCount);
    }

    @Unique
    private void rbip$placeTab(RecipeBookTabButton widget, int slot) {
        RecipeBookIsPainExtendedConfig config = RecipeBookIsPainExtendedConfig.get();
        if (!config.extendedFeatures()) {
            this.rbip$placeNormalTab(widget, slot);
            return;
        }

        if (slot < RBIP_LEFT_TOTAL_SLOTS) {
            this.rbip$placeNormalTab(widget, slot);
        } else if (slot < RBIP_LEFT_TOTAL_SLOTS + RBIP_TOP_SLOTS) {
            int topSlot = slot - RBIP_LEFT_TOTAL_SLOTS;
            ((RecipeGroupButtonPlacementAccess) widget).rbip$setPlacement(RecipeGroupButtonPlacement.TOP);
            int x = this.rbip$getTopTabX(topSlot);
            int y = this.rbip$getTopTabY();
            widget.setRectangle(RBIP_ROTATED_TAB_WIDTH, RBIP_ROTATED_TAB_HEIGHT, x, y);
        } else if (slot < RBIP_LEFT_TOTAL_SLOTS + RBIP_TOP_SLOTS + RBIP_BOTTOM_SLOTS) {
            int bottomSlot = slot - RBIP_LEFT_TOTAL_SLOTS - RBIP_TOP_SLOTS;
            ((RecipeGroupButtonPlacementAccess) widget).rbip$setPlacement(RecipeGroupButtonPlacement.BOTTOM);
            int x = this.rbip$getBottomTabX(bottomSlot);
            int y = this.rbip$getBottomTabY();
            widget.setRectangle(RBIP_ROTATED_TAB_WIDTH, RBIP_ROTATED_TAB_HEIGHT, x, y);
        } else {
            this.rbip$placeNormalTab(widget, slot);
        }
    }

    @Unique
    private void rbip$placeNormalTab(RecipeBookTabButton widget, int slot) {
        ((RecipeGroupButtonPlacementAccess) widget).rbip$setPlacement(RecipeGroupButtonPlacement.NORMAL);
        int x = this.rbip$getTabX();
        int y = this.rbip$getTabY() + RBIP_TAB_HEIGHT * slot;
        widget.setRectangle(RBIP_TAB_WIDTH, RBIP_TAB_HEIGHT, x, y);
    }

    @Unique
    private void rbip$resetTabPlacement(RecipeBookTabButton widget) {
        ((RecipeGroupButtonPlacementAccess) widget).rbip$setPlacement(RecipeGroupButtonPlacement.NORMAL);
        widget.setSize(RBIP_TAB_WIDTH, RBIP_TAB_HEIGHT);
    }

    @Unique
    private int rbip$getTabX() {
        return this.rbip$getBookX() - 30;
    }

    @Unique
    private int rbip$getTabY() {
        return this.rbip$getBookY() + 3;
    }

    @Unique
    private int rbip$getPageControlX() {
        if (RecipeBookIsPainExtendedConfig.get().extendedFeatures()) {
            return this.rbip$getBookX() - 28;
        }
        return this.rbip$getBookX() + 5;
    }

    @Unique
    private int rbip$getPageControlY() {
        return this.rbip$getBookY() - 12;
    }

    @Unique
    private int rbip$getBookX() {
        return (this.width - RBIP_BOOK_WIDTH) / 2 - this.xOffset;
    }

    @Unique
    private int rbip$getBookY() {
        return (this.height - RBIP_BOOK_HEIGHT) / 2;
    }

    @Unique
    private int rbip$getBottomTabX(int slot) {
        return this.rbip$getHorizontalTabStartX() + slot * RBIP_EXTENDED_SLOT_STEP;
    }

    @Unique
    private int rbip$getBottomTabY() {
        return this.rbip$getBookY() + RBIP_BOOK_HEIGHT - 5;
    }

    @Unique
    private int rbip$getTopTabX(int slot) {
        return this.rbip$getHorizontalTabStartX() + slot * RBIP_EXTENDED_SLOT_STEP;
    }

    @Unique
    private int rbip$getTopTabY() {
        return this.rbip$getBookY() - RBIP_ROTATED_TAB_HEIGHT + 5;
    }

    @Unique
    private int rbip$getHorizontalTabStartX() {
        return this.rbip$getBookX() + (RBIP_BOOK_WIDTH - RBIP_TOP_SLOTS * RBIP_ROTATED_TAB_WIDTH) / 2;
    }

    // ── 标签栏翻页动画（RBIP）────────────────────────────────────
    //
    // 与配方区翻页动画同一套「目标追逐」模型，只是追逐量换成每个标签的伸出度：
    //   退场标签：静止位 → 向书体中心平移，直到被配方书盖住（extend 1 → 0）
    //   进场标签：书体内部 → 沿各自方向滑出到既定位置（extend 0 → 1）
    // 两步并行；进场恒画在退场之上；搜索标签不参与（始终保持不变）。
    // 裁剪框固定为「静止位矩形」，所以静止时与原版逐像素一致，平移出去的部分被书体
    // 边缘吃掉 —— 视觉上就是滑进书皮底下，不存在"直接替换标签图标"的露馅帧。

    /** 只有用户翻页（页控件 / 滚轮）才播动画；翻页动画总开关（{@code pageAnimation}）管着它。 */
    @Unique
    private static boolean rbip$tabFlipEnabled() {
        return com.alonie.brbe.BetterRecipeBook.config == null
                || com.alonie.brbe.BetterRecipeBook.config.pageAnimation.pageAnimationEnabled;
    }

    /**
     * 速率（1/秒）：与配方区动画共用 {@code 6.2 / 时长}，再乘 √跨度 ——
     * <b>跨度越大越快</b>（连滚时跨度累加），同时保证单页翻动仍看得清标签图标。
     */
    @Unique
    private float rbip$tabFlipRate() {
        float duration = 0.5F;
        if (com.alonie.brbe.BetterRecipeBook.config != null) {
            duration = com.alonie.brbe.BetterRecipeBook.config.pageAnimationDuration;
        }
        float span = Math.max(1.0F, this.rbip$tabFlipSpan);
        return RBIP_TAB_FLIP_BASE_RATE * (float) Math.sqrt(span) / Math.max(0.05F, duration);
    }

    /** 两页之间的跨度（页数）：开了循环滚动时取绕回方向的较短距离。 */
    @Unique
    private static int rbip$pageDistance(int from, int to, int pageCount) {
        int direct = Math.abs(to - from);
        if (pageCount > 1 && com.alonie.brbe.BetterRecipeBook.config != null
                && com.alonie.brbe.BetterRecipeBook.config.scrolling.scrollAround) {
            return Math.min(direct, pageCount - direct);
        }
        return direct;
    }

    /** 翻页前：把当前可见的非搜索标签登记为退场（未参与过动画的以"当前位置"为静止位）。 */
    @Unique
    private void rbip$captureFlipOutgoing() {
        for (RecipeBookTabButton widget : this.tabButtons) {
            if (!widget.visible || !rbip$isFlipTab(widget)) continue;
            TabFlipState state = ((RecipeGroupButtonFlipAccess) widget).rbip$flipState();
            if (!state.tracked) {
                state.resetAt(widget.getX(), widget.getY(),
                        ((RecipeGroupButtonPlacementAccess) widget).rbip$getPlacement());
                state.extend = 1.0F;
            }
            state.tracked = true;
            // 默认退场；下面若发现它仍在新页，会由 rbip$syncFlipTargets 改回 1
            state.target = 0.0F;
        }
    }

    /**
     * 重排结束：是用户翻页且真的换了页 → 启动（或续接）翻页动画；
     * 已在跑动画时（哪怕这次不是用户翻页）同步一次目标，避免标签集合变化后卡住。
     *
     * <p>续接是连续的：正在缩进的标签就地改目标继续走，不会跳回静止位。</p>
     */
    @Unique
    private void rbip$finishPagination() {
        boolean userFlip = this.rbip$tabFlipUserFlip;
        int previousPage = this.rbip$tabFlipFromPage;
        this.rbip$tabFlipUserFlip = false;

        boolean start = userFlip && rbip$tabFlipEnabled()
                && this.rbip$pageCount > 1 && this.rbip$page != previousPage;
        if (start) {
            if (!this.rbip$tabFlipActive) {
                this.rbip$tabFlipSpan = 0.0F;
            }
            this.rbip$tabFlipSpan += rbip$pageDistance(previousPage, this.rbip$page, this.rbip$pageCount);
            this.rbip$tabFlipActive = true;
        }
        if (start || this.rbip$tabFlipActive) {
            rbip$syncFlipTargets();
        }
    }

    /** 按「是否在本页可见」给参与标签定目标：可见 = 伸出（1）、不可见 = 缩进（0）。 */
    @Unique
    private void rbip$syncFlipTargets() {
        for (RecipeBookTabButton widget : this.tabButtons) {
            if (!rbip$isFlipTab(widget)) continue;
            TabFlipState state = ((RecipeGroupButtonFlipAccess) widget).rbip$flipState();
            if (widget.visible) {
                state.resetAt(widget.getX(), widget.getY(),
                        ((RecipeGroupButtonPlacementAccess) widget).rbip$getPlacement());
                if (!state.tracked) {
                    // 新登场的标签从书体内部伸出 —— 选中态一并归零：它可能是被"瞬间隐藏"的
                    // 选中标签（blend 冻在 1），不归零的话伸出过程会带着选中态一起涨满，
                    // 落地时就没有渐变可放了（用户 2026-10-04 反馈的"闪到书皮上"）。
                    state.extend = 0.0F;
                    state.tracked = true;
                    ((RecipeGroupButtonFlipAccess) widget).rbip$beginFlipIn();
                }
                state.target = 1.0F;
            } else if (state.tracked) {
                state.target = 0.0F;
            }
        }
    }

    /** 搜索标签永远不参与动画（用户要求：搜索标签始终保持不变）。 */
    @Unique
    private boolean rbip$isFlipTab(RecipeBookTabButton widget) {
        return widget != this.rbip$pinnedTab
                && !(widget.getCategory() instanceof SearchRecipeBookCategory);
    }

    /** 收尾：清空全部参与状态，标签随即回到原版渲染路径（选中态也自动恢复）。 */
    @Unique
    private void rbip$endTabFlip() {
        this.rbip$tabFlipActive = false;
        this.rbip$tabFlipSpan = 0.0F;
        this.rbip$tabFlipUserFlip = false;
        for (RecipeBookTabButton widget : this.tabButtons) {
            TabFlipState state = ((RecipeGroupButtonFlipAccess) widget).rbip$flipState();
            state.tracked = false;
            state.extend = 1.0F;
            state.target = 1.0F;
        }
    }

    /**
     * 画一个参与动画的标签：临时把它挪到动画位置（只沿单轴平移）、套上裁剪框、
     * 暂停它的选中态，画完原样还原。图标 / 标签 / 固定标记是一个整体，一起平移。
     */
    @Unique
    private void rbip$drawFlippingTab(GuiGraphicsExtractor context, RecipeBookTabButton widget, TabFlipState state,
                                      int mouseX, int mouseY, float delta) {
        int shift = TabFlipGeometry.shift(state);
        if (shift >= TabFlipGeometry.OUTSIDE_EXTENT) return;    // 已完全没入书体，整块被裁掉
        TabFlipGeometry.clip(state.baseX, state.baseY, state.placement, this.rbip$tabFlipClip);

        RecipeGroupButtonPlacementAccess placement = (RecipeGroupButtonPlacementAccess) widget;
        int savedX = widget.getX();
        int savedY = widget.getY();
        int savedWidth = widget.getWidth();
        int savedHeight = widget.getHeight();
        boolean savedVisible = widget.visible;
        RecipeGroupButtonPlacement savedPlacement = placement.rbip$getPlacement();
        // 「选中标签暂时取消选中状态」不再靠临时 unselect 实现：翻页期间选中态只减不增
        // （见 RecipeGroupButtonMixin#rbip$advanceSelectBlend）—— 退场随缩进淡出、进场按住
        // 不动，等落地、裁剪解除后再淡入，`selected` 字段全程不动。
        widget.setRectangle(TabFlipGeometry.width(state.placement), TabFlipGeometry.height(state.placement),
                TabFlipGeometry.renderX(state), TabFlipGeometry.renderY(state));
        widget.visible = true;
        placement.rbip$setPlacement(state.placement);

        // **整块套 scissor**：翻页移动中的标签（本体、图标、pin、以及正在渐变的选中形态）
        // 一律裁在书体边缘 —— 配方书始终盖在移动的标签上面。
        // 例外：**登场且选中**、且已进入停靠窗口的标签（{@code rbip$revealsDock}）不套外层
        // scissor —— 它由按钮自己拆成「本体（裁在书皮边缘）+ 固定的停靠窗口（按剩余位移渐显）」，
        // 于是标签一边缓缓停靠、一边把压在书皮上的那一小块显出来，两段动作合而为一。
        boolean dockReveal = ((RecipeGroupButtonFlipAccess) widget).rbip$revealsDock();
        if (!dockReveal) {
            context.enableScissor(this.rbip$tabFlipClip[0], this.rbip$tabFlipClip[1],
                    this.rbip$tabFlipClip[2], this.rbip$tabFlipClip[3]);
        }
        widget.extractRenderState(context, mouseX, mouseY, delta);
        if (!dockReveal) {
            context.disableScissor();
        }

        widget.visible = savedVisible;
        widget.setRectangle(savedWidth, savedHeight, savedX, savedY);
        placement.rbip$setPlacement(savedPlacement);
    }

    /** 「隐藏翻页按钮」（默认关）：开启时 RBIP 标签栏的翻页按钮整块不画、也不吞点击。
     *  只影响这两个箭头本身——标签区域的滚轮翻页（{@link #rbip$isMouseOverAnyVisibleTab}）不变。 */
    @Unique
    private static boolean rbip$pageButtonsHidden() {
        return com.alonie.brbe.BetterRecipeBook.config != null
                && com.alonie.brbe.BetterRecipeBook.config.rbip.hideTabPageButtons;
    }

    @Unique
    private void rbip$drawPageControl(GuiGraphicsExtractor context, int x, int y, boolean next, boolean active, int mouseX, int mouseY) {
        int u = next ? 14 : 0;
        if (active && this.rbip$isInside(mouseX, mouseY, x, y, RBIP_PAGE_BUTTON_WIDTH, RBIP_PAGE_BUTTON_HEIGHT)) {
            u += 28;
        }

        int v = active ? 0 : 13;
        context.blit(RenderPipelines.GUI_TEXTURED, RBIP_PAGE_BUTTONS, x, y, u, v, RBIP_PAGE_BUTTON_WIDTH, RBIP_PAGE_BUTTON_HEIGHT, 256, 256);
    }

    @Unique
    private boolean rbip$isInside(int x, int y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    @Unique
    private boolean rbip$isMouseOverAnyVisibleTab(double mouseX, double mouseY) {
        // 固定滚动区域 = 创造模式标签的完整容纳空间（不依赖实际放置的标签）：
        // 左侧 6 槽整列 + 顶部整行 + 底部整行（含向书体**外侧**的 20px 余量）。
        // 只要鼠标落在这些槽位区域内（即便该处没有标签）即可翻页。
        if (this.rbip$isInside(mouseX, mouseY, this.rbip$getTabX(), this.rbip$getTabY(),
                RBIP_TAB_WIDTH, RBIP_LEFT_TOTAL_SLOTS * RBIP_TAB_HEIGHT)) {
            return true;
        }
        // 上下两条：余量只加在书体外侧，内侧止于标签自身边缘。旧写法以标签矩形
        // 为中心上下各扩 20px，于是下方那条伸进书体 20px、盖住配方网格最后一行 ——
        // 在配方区滚动会被当成"滚标签"吃掉（2026-09-25 修正）。
        int horizX = this.rbip$getHorizontalTabStartX();
        int horizW = RBIP_TOP_SLOTS * RBIP_EXTENDED_SLOT_STEP;
        int topStripY = this.rbip$getTopTabY() - RBIP_HORIZONTAL_SCROLL_OUTWARD_PADDING;
        int stripH = RBIP_ROTATED_TAB_HEIGHT + RBIP_HORIZONTAL_SCROLL_OUTWARD_PADDING;
        if (this.rbip$isInside(mouseX, mouseY, horizX, topStripY, horizW, stripH)) {
            return true;
        }
        int bottomStripY = this.rbip$getBottomTabY();
        if (this.rbip$isInside(mouseX, mouseY, horizX, bottomStripY, horizW, stripH)) {
            return true;
        }
        // The turn-page buttons themselves are also a scroll zone.
        int btnW = RBIP_PAGE_BUTTON_WIDTH * 2 + 15;
        return this.rbip$isInside(mouseX, mouseY, this.rbip$pageControlX, this.rbip$pageControlY,
                btnW, RBIP_PAGE_BUTTON_HEIGHT);
    }

    @Unique
    private boolean rbip$isInside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }
}

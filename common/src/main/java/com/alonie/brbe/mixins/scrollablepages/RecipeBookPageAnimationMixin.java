package com.alonie.brbe.mixins.scrollablepages;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeButtonAccessor;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.PageAnimationEdges;
import com.alonie.brbe.util.PageFlipDirection;
import com.alonie.brbe.util.PartialCraftingUtil;
import com.alonie.brbe.util.RecipeBookPageAnimBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.21.1 翻页动画（快照池 + @Redirect 渲染 + 边缘挤压视效）。
 *
 * <p>对照 1.21.11 的 RecipeBookPageAnimationMixin：1.21.1 的 RecipeButton 用
 * 无参构造 + {@code init(RecipeCollection, RecipeBookPage)}（2 参），无
 * SlotSelectTime/RecipeDisplayId 体系——快照池与动画核心按 1.21.1 API 重写。
 *
 * <p>**关键设计（与旧版平移实现不同）**：动画期间完全不移动真实按钮
 * （{@code @Redirect RecipeButton.render}），而是用两个快照池在视觉页
 * {@code brbe$visualPage} 的位置渲染「滑出页 + 滑入页」——真实按钮始终停在
 * 网格基准位，动画结束/被打断不会残留偏移（旧版直接 setPosition 平移真实
 * 按钮，结束时不归位 → 翻页后按钮永久位移出网格、「页面内容消失」）。</p>
 *
 * <p>视觉与 1.21.11 完整版一致：配方滑出视窗边界时**边缘挤压**（宽度收窄、
 * 边界独立渲染，用 {@link PageAnimationEdges} 读 {@code animation/edge_width.json}
 * 的左右边距）、残缺配方红罩、已固定配方 pin 图标（网格 scissor 外补画）；
 * tooltip 跟随光标命中的快照按钮。</p>
 *
 * <p>受 {@code brbe.toml} 的 {@code pageAnimation.pageAnimationEnabled}（开关）
 * 与 {@code pageAnimationDuration}（时长，秒）控制。</p>
 */
@Mixin(RecipeBookPage.class)
public abstract class RecipeBookPageAnimationMixin {

    private static final float TICKS_PER_SECOND = 20.0F;
    private static final int PAGE_SLIDE_DISTANCE = 125;
    /** 连续翻页判定窗口：距上次翻页不足该帧数视为连点（进入追逐延展）。 */
    private static final int CHASE_WINDOW_FRAMES = 10;
    /** 追逐模式下每页响应时长（秒），独立于配置时长，保证连点滑动连贯。 */
    private static final float CHASE_PAGE_DURATION = 0.25F;
    private static final float SNAP_THRESHOLD = 0.002F;
    /** 单次翻页预计总时长上限：超过 2×配置时长则跳过中间页压缩追逐。 */
    private static final float MAX_ANIM_MULTIPLIER = 2.0F;
    /** 最后一页指数减速所占时长（×配置时长）。 */
    private static final float FINAL_DECEL_MULTIPLIER = 1.0F;

    /** 网格 scissor 原点（render 的 areaLeft/areaTop 参数，即页面背景左上角）。 */
    private static final int GRID_LEFT_PAD = 11;
    private static final int GRID_TOP_PAD = 31;
    private static final int GRID_WIDTH = 125;
    private static final int GRID_HEIGHT = 100;

    // 1.21.1 RecipeButton 的槽位 sprite 是 private static 字段——按 javap 核对的
    // 原版 id 直接构造（recipe_book/slot_*）。
    private static final ResourceLocation SLOT_MANY_CRAFTABLE =
            ResourceLocation.withDefaultNamespace("recipe_book/slot_many_craftable");
    private static final ResourceLocation SLOT_CRAFTABLE =
            ResourceLocation.withDefaultNamespace("recipe_book/slot_craftable");
    private static final ResourceLocation SLOT_MANY_UNCRAFTABLE =
            ResourceLocation.withDefaultNamespace("recipe_book/slot_many_uncraftable");
    private static final ResourceLocation SLOT_UNCRAFTABLE =
            ResourceLocation.withDefaultNamespace("recipe_book/slot_uncraftable");

    @Shadow private int currentPage;
    @Shadow private int totalPages;
    @Shadow private List<RecipeButton> buttons;
    @Shadow private List<RecipeCollection> recipeCollections;
    @Shadow private Minecraft minecraft;
    @Shadow private RecipeButton hoveredButton;

    /** 滑出页快照池（视觉当前页）。 */
    @Unique private final List<RecipeButton> brbe$snapshotButtons = new ArrayList<>(20);
    /** 滑入页快照池（视觉下一页）。分池避免悬停捕获的按钮内容被另一页覆盖。 */
    @Unique private final List<RecipeButton> brbe$snapshotButtonsIn = new ArrayList<>(20);
    /** 动画期间光标命中的快照按钮，渲染末尾覆盖 hoveredButton 驱动 tooltip。 */
    @Unique private RecipeButton brbe$animHovered;
    /** 动画期间已固定配方按钮的 (x, y) 收集（网格 scissor 关闭后统一绘制 pin 图标）。 */
    @Unique private final List<int[]> brbe$animPinIcons = new ArrayList<>(4);
    @Unique private boolean brbe$animActive;
    @Unique private boolean brbe$animChase;
    @Unique private boolean brbe$animBackward;
    @Unique private float brbe$visualPage;
    @Unique private float brbe$travelTarget;
    /** 压缩追逐时的每帧预算页速（页/秒），翻页时按跨度与预算时长折算。 */
    @Unique private float brbe$animBulkSpeed;
    @Unique private int brbe$frameCounter;
    @Unique private int brbe$lastFlipFrame = -100;
    @Unique private int brbe$lastRenderedPage = -1;
    @Unique private int brbe$scissorLeft;
    @Unique private int brbe$scissorTop;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void brbe$initSnapshotButtons(CallbackInfo ci) {
        for (int i = 0; i < 20; i++) {
            this.brbe$snapshotButtons.add(new RecipeButton());
            this.brbe$snapshotButtonsIn.add(new RecipeButton());
        }
    }

    /** 窗口尺寸变化/重新打开会触发 init 重新布局：重置动画，避免旧视觉页与网格错配。 */
    @Inject(method = "init", at = @At("HEAD"))
    private void brbe$resetOnReinit(Minecraft minecraftClient, int parentLeft, int parentTop, CallbackInfo ci) {
        this.brbe$animActive = false;
        this.brbe$animChase = false;
        this.brbe$animBackward = false;
        this.brbe$visualPage = this.currentPage;
        this.brbe$lastFlipFrame = -100;
        this.brbe$frameCounter = 0;
    }

    /** 用户翻页检测（updateButtonsForPage HEAD：此处 currentPage 已变）。 */
    @Inject(method = "updateButtonsForPage", at = @At("HEAD"))
    private void brbe$detectFlip(CallbackInfo ci) {
        boolean userFlip = RecipeBookPageAnimBridge.consumeUserFlip();
        if (!brbe$animationEnabled()) {
            this.brbe$animActive = false;
            this.brbe$animChase = false;
            this.brbe$lastRenderedPage = this.currentPage;
            return;
        }
        if (this.currentPage == this.brbe$lastRenderedPage) return;
        if (!userFlip) {
            // 非用户翻页（恢复浏览记录/配方刷新）：直接切换，不启动滑动动画
            this.brbe$animActive = false;
            this.brbe$animChase = false;
            this.brbe$visualPage = this.currentPage;
            this.brbe$lastRenderedPage = this.currentPage;
            return;
        }
        // 用户翻页（相邻或跨多页均动画）：决定方向并计算旅行目标（允许越界表达绕回），
        // 由追逐机制滑过全部中间页，渲染时页索引取模。
        this.brbe$animBackward = PageFlipDirection.backward(
                Math.round(this.brbe$visualPage), this.currentPage, this.totalPages,
                BetterRecipeBook.config != null && BetterRecipeBook.config.scrolling.scrollAround);
        this.brbe$travelTarget = this.currentPage;
        if (this.brbe$animBackward) {
            if (this.brbe$travelTarget > this.brbe$visualPage) {
                this.brbe$travelTarget -= this.totalPages;
            }
        } else {
            if (this.brbe$travelTarget < this.brbe$visualPage) {
                this.brbe$travelTarget += this.totalPages;
            }
        }
        // 压缩追逐预算：按跨度与预算时长折算每帧最小页速（跳过中间页）
        this.brbe$animBulkSpeed = Math.abs(this.brbe$travelTarget - this.brbe$visualPage) / brbe$bulkBudgetSeconds();
        // 连续翻页检测：距上次翻页较近（连点）→ 进入追逐延展模式，滑动连贯不逐页独立
        if (this.brbe$frameCounter - this.brbe$lastFlipFrame < CHASE_WINDOW_FRAMES) {
            this.brbe$animChase = true;
        }
        this.brbe$lastFlipFrame = this.brbe$frameCounter;
        this.brbe$animActive = true;
        this.brbe$lastRenderedPage = this.currentPage;
    }

    /** 渲染 HEAD：推进视觉页动画（在 vanilla 绘制按钮之前，保证同帧位置一致）。 */
    @Inject(method = "render", at = @At("HEAD"))
    private void brbe$advanceAnimation(GuiGraphics gui, int areaLeft, int areaTop,
                                       int mouseX, int mouseY, float delta, CallbackInfo ci) {
        this.brbe$scissorLeft = areaLeft;
        this.brbe$scissorTop = areaTop;
        this.brbe$frameCounter++;
        this.brbe$animHovered = null;
        if (!this.brbe$animActive) return;
        float deltaS = delta / TICKS_PER_SECOND;
        float remaining = this.brbe$travelTarget - this.brbe$visualPage;
        float absd = Math.abs(remaining);
        if (absd < SNAP_THRESHOLD) {
            this.brbe$visualPage = this.currentPage;
            this.brbe$animActive = false;
            this.brbe$animChase = false;
            return;
        }
        // 统一速度曲线：追逐只在远离目标（absd≥1）时用快 rate 追赶；一旦进入
        // 最后一页（absd<1）即与单页共用同一条指数减速曲线，减速弧线完全一致。
        float base = (this.brbe$animChase && absd >= 1.0F) ? 6.2F / CHASE_PAGE_DURATION : 6.2F / brbe$animationDuration();
        float fraction = 1.0F - (float) Math.exp(-base * deltaS);
        float cap = 0.45F + (float) Math.sqrt(absd) * 0.12F;
        float move = Math.min(Math.min(absd * fraction, cap), absd);
        // 跳过中间页压缩追逐：大跨度跳转时按预算时长折算每帧最小页距
        float bulkMove = Math.min(this.brbe$animBulkSpeed * deltaS, absd - 1.0F);
        move = Math.max(move, bulkMove);
        this.brbe$visualPage += Math.signum(remaining) * move;
    }

    /** 渲染 RETURN：tooltip 跟随光标命中的快照按钮（视觉内容），无命中则清空避免错页。 */
    @Inject(method = "render", at = @At("RETURN"))
    private void brbe$tooltipFollowSnapshot(GuiGraphics gui, int areaLeft, int areaTop,
                                            int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.brbe$animActive) {
            this.hoveredButton = this.brbe$animHovered;
        }
    }

    /**
     * 动画期间用快照池替代真实按钮渲染：真实按钮的位置/内容完全不动，动画结束时
     * 直接放行原版 —— 不存在旧版「结束不归位」的残留偏移问题。
     */
    @Redirect(method = "render",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeButton;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
    private void brbe$renderButton(RecipeButton button, GuiGraphics gui, int mouseX, int mouseY, float delta) {
        if (!this.brbe$animActive) {
            button.render(gui, mouseX, mouseY, delta);
            return;
        }
        int k = this.buttons.indexOf(button);
        if (k < 0 || k >= this.brbe$snapshotButtons.size()) {
            button.render(gui, mouseX, mouseY, delta);
            return;
        }
        int basePage = (int) Math.floor(this.brbe$visualPage);
        float frac = this.brbe$visualPage - basePage;
        RecipeButton snap = this.brbe$snapshotButtons.get(k);
        RecipeButton snapIn = this.brbe$snapshotButtonsIn.get(k);
        this.brbe$animPinIcons.clear();
        int boundLeft = this.brbe$scissorLeft + GRID_LEFT_PAD;
        int boundTop = this.brbe$scissorTop + GRID_TOP_PAD;
        int boundRight = this.brbe$scissorLeft + GRID_LEFT_PAD + GRID_WIDTH;
        int boundBottom = this.brbe$scissorTop + GRID_TOP_PAD + GRID_HEIGHT;
        gui.enableScissor(boundLeft, boundTop, boundRight, boundBottom);
        // 视觉当前页向左滑出 + 下一页从右滑入（边缘挤压视效）
        brbe$renderVisualSquashed(snap, k, basePage,
                button.getX() + Math.round(-frac * PAGE_SLIDE_DISTANCE), button.getY(),
                gui, mouseX, mouseY, delta);
        brbe$renderVisualSquashed(snapIn, k, basePage + 1,
                button.getX() + Math.round((1.0F - frac) * PAGE_SLIDE_DISTANCE), button.getY(),
                gui, mouseX, mouseY, delta);
        gui.disableScissor();
        // 已固定配方的 pin 图标绘制在网格 scissor 之外（与静态路径相同的裁剪状态）：
        // 超出配方区的悬出部分不被裁剪。
        for (int[] p : this.brbe$animPinIcons) {
            gui.blitSprite(BRBTextures.RECIPE_BOOK_PIN_SPRITE, p[0] - 4, p[1] - 4, 32, 32);
        }
    }

    /**
     * 边缘挤压渲染（与 1.21.11 版本一致）：配方滑出视窗边界时，内容裁剪在
     * [effX, edgeRight) 内（中间随滑动变短），左右边界 2px 独立渲染（边框完整
     * 不缩放）；图标在边界线之后渲染，完整跟随配方滑出视窗。
     */
    @Unique
    private void brbe$renderVisualSquashed(RecipeButton snap, int k, int page, int x, int y,
                                           GuiGraphics gui, int mouseX, int mouseY, float f) {
        int wrapped = brbe$wrapPage(page);
        int idx = wrapped * 20 + k;
        if (idx >= this.recipeCollections.size()) {
            snap.visible = false;
            return;
        }
        snap.init(this.recipeCollections.get(idx), (RecipeBookPage) (Object) this);
        snap.visible = true;
        int leftBound = this.brbe$scissorLeft + GRID_LEFT_PAD;
        int rightBound = this.brbe$scissorLeft + GRID_LEFT_PAD + GRID_WIDTH;
        int effX = x;
        int effW = 25;
        if (x < leftBound) {
            effX = leftBound;
            effW = x + 25 - leftBound;
        } else if (x + 25 > rightBound) {
            effX = x;
            effW = rightBound - x;
        }
        if (effW <= 0) {
            snap.visible = false;
            return;
        }
        // 动画期间 tooltip 跟随移动配方：记录可见有效区内光标命中的快照按钮
        if (mouseX >= effX && mouseX < effX + effW && mouseY >= y && mouseY < y + 25) {
            this.brbe$animHovered = snap;
        }
        RecipeCollection c = snap.getCollection();
        boolean many = c.getRecipes(false).size() > 1;
        ResourceLocation sprite = c.hasCraftable()
                ? (many ? SLOT_MANY_CRAFTABLE : SLOT_CRAFTABLE)
                : (many ? SLOT_MANY_UNCRAFTABLE : SLOT_UNCRAFTABLE);
        int edgeRight = effX + effW;
        RecipeHolder<?> current = brbe$currentRecipeOf(snap);
        boolean isPartial = current != null && PartialCraftingUtil.isPartiallyCraftable(c, current);
        if (effW < 25) {
            // 伪压缩：内容 scissor 右边界收窄 1px（原版 sprite 最右列顶部 1px 是透明的）
            gui.enableScissor(effX, y, edgeRight - 1, y + 25);
            gui.blitSprite(sprite, x, y, 25, 25);
            if (isPartial) {
                gui.fill(effX + 1, y + 1, edgeRight - 1, y + 24, 0x60FF3333);
            }
            gui.disableScissor();
            brbe$renderItemIcon(snap, gui, x, y);
            // 移动方向的前方边缘盖住后方边缘：配方左移时左边界最后渲染（在上层）
            boolean movingLeft = x < effX;
            if (movingLeft) {
                gui.enableScissor(Math.max(edgeRight - PageAnimationEdges.right(), effX), y, edgeRight, y + 25);
                gui.blitSprite(sprite, edgeRight - 25, y, 25, 25);
                gui.disableScissor();
                gui.enableScissor(effX, y, Math.min(effX + PageAnimationEdges.left(), edgeRight), y + 25);
                gui.blitSprite(sprite, effX, y, 25, 25);
                gui.disableScissor();
            } else {
                gui.enableScissor(effX, y, Math.min(effX + PageAnimationEdges.left(), edgeRight), y + 25);
                gui.blitSprite(sprite, effX, y, 25, 25);
                gui.disableScissor();
                gui.enableScissor(Math.max(edgeRight - PageAnimationEdges.right(), effX), y, edgeRight, y + 25);
                gui.blitSprite(sprite, edgeRight - 25, y, 25, 25);
                gui.disableScissor();
            }
        } else {
            gui.blitSprite(sprite, effX, y, 25, 25);
            if (isPartial) {
                gui.fill(effX + 1, y + 1, effX + 24, y + 24, 0x60FF3333);
            }
            if (effW > 20) {
                brbe$renderItemIcon(snap, gui, effX, y);
            }
        }
        // 已固定配方：收集 pin 图标位置（最上层绘制，见 brbe$renderButton 收尾）
        if (BetterRecipeBook.pinnedRecipeManager.isFullyPinned(c)) {
            this.brbe$animPinIcons.add(new int[] { effX, y });
        }
    }

    /** 渲染配方图标（快照按钮的轮循状态推进 + 图标布局复刻 renderWidget）。 */
    @Unique
    private void brbe$renderItemIcon(RecipeButton snap, GuiGraphics gui, int baseX, int baseY) {
        RecipeButtonAccessor acc = (RecipeButtonAccessor) snap;
        acc.brbe$setTime(acc.brbe$getTime() + 1.0F / TICKS_PER_SECOND);
        List<RecipeHolder<?>> ordered = acc.brbe$getOrderedRecipes();
        int size = ordered.size();
        if (size <= 0 || snap.getCollection() == null) return;
        int currentIndex = Mth.floor(acc.brbe$getTime() / 30.0F) % size;
        acc.brbe$setCurrentIndex(currentIndex);
        ItemStack stack = ordered.get(currentIndex).value()
                .getResultItem(snap.getCollection().registryAccess());
        int offset = 4;
        if (snap.getCollection().hasSingleResultItem() && size > 1) {
            gui.renderItem(stack, baseX + offset + 1, baseY + offset + 1, 0, 10);
            offset--;
        }
        gui.renderFakeItem(stack, baseX + offset, baseY + offset);
    }

    /** 当前显示的配方（轮循索引），用于残缺判定；空列表返回 null。 */
    @Unique
    private RecipeHolder<?> brbe$currentRecipeOf(RecipeButton snap) {
        RecipeButtonAccessor acc = (RecipeButtonAccessor) snap;
        List<RecipeHolder<?>> ordered = acc.brbe$getOrderedRecipes();
        if (ordered.isEmpty()) return null;
        int idx = Math.floorMod(Mth.floor(acc.brbe$getTime() / 30.0F), ordered.size());
        return ordered.get(idx);
    }

    @Unique
    private int brbe$wrapPage(int page) {
        int total = this.totalPages;
        if (total <= 1) {
            return 0;
        }
        return ((page % total) + total) % total;
    }

    @Unique
    private boolean brbe$animationEnabled() {
        return BetterRecipeBook.config != null
                && BetterRecipeBook.config.pageAnimation != null
                && BetterRecipeBook.config.pageAnimation.pageAnimationEnabled;
    }

    @Unique
    private float brbe$animationDuration() {
        if (BetterRecipeBook.config != null) {
            return Math.max(0.05F, BetterRecipeBook.config.pageAnimationDuration);
        }
        return 0.1F;
    }

    /** 大跨度跳转的「跳过中间页」预算时长（秒）：2×配置时长扣除末页指数减速。 */
    @Unique
    private float brbe$bulkBudgetSeconds() {
        float duration = brbe$animationDuration();
        return Math.max(0.05F, (MAX_ANIM_MULTIPLIER - FINAL_DECEL_MULTIPLIER) * duration);
    }
}

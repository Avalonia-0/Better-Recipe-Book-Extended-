package com.alonie.brbe.mixins.scrollablepages;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.generic.pins.PinnableRecipeCollection;
import com.alonie.brbe.mixins.accessors.RecipeButtonAccessor;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.brbe.util.PageAnimationEdges;
import com.alonie.brbe.util.PageFlipDirection;
import com.alonie.brbe.util.PartialCraftingUtil;
import com.alonie.brbe.util.RecipeBookPageAnimBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.gui.screens.recipebook.SlotSelectTime;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
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
 * 原版合成台配方书（{@link RecipeBookPage}）翻页平滑滑动动画。
 *
 * <p>与自研配方书（酿造/锻造台）同一套机制：**目标追逐模型**——
 * {@code brbe$visualPage}（浮点视觉页）持续朝逻辑页 {@code currentPage} 逼近，
 * 落后越多滑得越快，最后一页缓动减速。连点翻页不重置、不跳变、自然加速。</p>
 *
 * <p>翻页统一走 {@code updateButtonsForPage}（点击/滚轮/配方刷新都经过这里），
 * 在其 HEAD 检测 currentPage 变化并启动动画；渲染时 {@code @Redirect}
 * {@code RecipeButton.render} 调用，动画期间用独立的视觉渲染池
 * （{@code brbe$snapshotButtons}）动态渲染视觉当前页与下一页，scissor 只包住
 * 按钮网格区（不影响页码、箭头、替代配方弹层）。非动画路径完全放行原版。</p>
 *
 * <p>受 {@code zzzbrbe.toml} 的 {@code pageAnimation.pageAnimationEnabled}（开关）
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

    @Shadow
    private int currentPage;
    @Shadow
    private int totalPages;
    @Shadow
    private List<RecipeButton> buttons;
    @Shadow
    private List<RecipeCollection> recipeCollections;
    @Shadow
    private boolean isFiltering;
    @Shadow
    private Minecraft minecraft;
    @Shadow
    private RecipeButton hoveredButton;

    @Unique
    private final List<RecipeButton> brbe$snapshotButtons = new ArrayList<>(20);
    /** 动画期间已固定配方按钮的 (x, y) 收集（每按钮一次渲染清空），网格 scissor
     *  关闭后统一绘制 pin 图标，避免超出配方区的悬出部分被裁剪。 */
    @Unique
    private final List<int[]> brbe$animPinIcons = new ArrayList<>(4);
    /** 动画期间光标命中的 snapshot 按钮，渲染末尾覆盖 hoveredButton 驱动 tooltip。 */
    @Unique
    private RecipeButton brbe$animHovered;
    @Unique
    private boolean brbe$animActive;
    @Unique
    private boolean brbe$animChase;
    @Unique
    private boolean brbe$animBackward;
    @Unique
    private float brbe$visualPage;
    @Unique
    private float brbe$travelTarget;
    /** 压缩追逐时的每帧预算页速（页/秒），翻页时按跨度与预算时长折算。 */
    @Unique
    private float brbe$animBulkSpeed;
    @Unique
    private int brbe$frameCounter;
    @Unique
    private int brbe$lastFlipFrame = -100;
    @Unique
    private int brbe$lastRenderedPage;
    @Unique
    private int[] brbe$baseX;
    @Unique
    private int[] brbe$baseY;
    @Unique
    private int brbe$scissorLeft;
    @Unique
    private int brbe$scissorTop;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void brbe$initSnapshotButtons(RecipeBookComponent<?> parent, SlotSelectTime slotSelectTime, boolean flag, CallbackInfo ci) {
        for (int i = 0; i < 20; i++) {
            this.brbe$snapshotButtons.add(new RecipeButton(slotSelectTime));
        }
    }

    /** 窗口尺寸变化/重新打开会触发 init 重新布局：重置动画，避免旧基准位置与偏移错配。 */
    @Inject(method = "init", at = @At("HEAD"))
    private void brbe$resetOnReinit(Minecraft minecraft, int parentLeft, int parentTop, CallbackInfo ci) {
        this.brbe$animActive = false;
        this.brbe$animChase = false;
        this.brbe$animBackward = false;
        this.brbe$visualPage = this.currentPage;
        this.brbe$lastFlipFrame = -100;
        this.brbe$frameCounter = 0;
        this.brbe$baseX = null;
    }

    @Inject(method = "updateButtonsForPage", at = @At("HEAD"))
    private void brbe$detectFlip(CallbackInfo ci) {
        boolean userFlip = RecipeBookPageAnimBridge.consumeUserFlip();
        if (!brbe$animationEnabled()) {
            this.brbe$animActive = false;
            this.brbe$animChase = false;
            this.brbe$lastRenderedPage = this.currentPage;
            return;
        }
        if (this.currentPage == this.brbe$lastRenderedPage) {
            return;
        }
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
        // 压缩追逐预算：按跨度与预算时长折算每帧最小页速（跳过中间页），小跨度时自然速度更快、不受影响
        this.brbe$animBulkSpeed = Math.abs(this.brbe$travelTarget - this.brbe$visualPage) / brbe$bulkBudgetSeconds();
        // 首次动画记录按钮基准位置；打断时保持原基准（此刻按钮可能处于滑动中途）
        if (!this.brbe$animActive) {
            this.brbe$baseX = new int[this.buttons.size()];
            this.brbe$baseY = new int[this.buttons.size()];
            for (int k = 0; k < this.buttons.size(); k++) {
                this.brbe$baseX[k] = this.buttons.get(k).getX();
                this.brbe$baseY[k] = this.buttons.get(k).getY();
            }
        }
        // 连续翻页检测：距上次翻页较近（连点）→ 进入追逐延展模式，滑动连贯不逐页独立
        if (this.brbe$frameCounter - this.brbe$lastFlipFrame < CHASE_WINDOW_FRAMES) {
            this.brbe$animChase = true;
        }
        this.brbe$lastFlipFrame = this.brbe$frameCounter;
        this.brbe$animActive = true;
        this.brbe$lastRenderedPage = this.currentPage;
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void brbe$advanceAnimation(GuiGraphics gui, int i, int j, int k, int l, float f, CallbackInfo ci) {
        this.brbe$scissorLeft = i;
        this.brbe$scissorTop = j;
        this.brbe$frameCounter++;
        this.brbe$animHovered = null;
        if (!this.brbe$animActive) {
            return;
        }
        float deltaS = f / TICKS_PER_SECOND;
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
        // 跳过中间页压缩追逐：大跨度跳转时按预算时长折算每帧最小页距，
        // 仅在预计总时长超过 2×配置时长时压过自然速度；末页（absd<1）交还指数减速
        float bulkMove = Math.min(this.brbe$animBulkSpeed * deltaS, absd - 1.0F);
        move = Math.max(move, bulkMove);
        this.brbe$visualPage += Math.signum(remaining) * move;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void brbe$tooltipFollowSnapshot(GuiGraphics gui, int i, int j, int k, int l, float f, CallbackInfo ci) {
        if (this.brbe$animActive) {
            // 动画期间 tooltip 跟随光标命中的 snapshot（视觉内容）；无命中则清空避免错页
            this.hoveredButton = this.brbe$animHovered;
        }
    }

    @Redirect(method = "render",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeButton;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
    private void brbe$renderButton(RecipeButton button, GuiGraphics gui, int x, int y, float f) {
        if (!this.brbe$animActive) {
            // 非动画路径：若按钮位置因动画中途打断而残留偏移，恢复基准
            if (this.brbe$baseX != null) {
                int k = this.buttons.indexOf(button);
                if (k >= 0 && k < this.brbe$baseX.length) {
                    button.setPosition(this.brbe$baseX[k], this.brbe$baseY[k]);
                }
            }
            button.render(gui, x, y, f);
            return;
        }
        int k = this.buttons.indexOf(button);
        if (k < 0 || k >= this.brbe$snapshotButtons.size() || this.brbe$baseX == null) {
            button.render(gui, x, y, f);
            return;
        }
        int basePage = (int) Math.floor(this.brbe$visualPage);
        float frac = this.brbe$visualPage - basePage;
        RecipeButton snap = this.brbe$snapshotButtons.get(k);
        this.brbe$animPinIcons.clear();
        gui.enableScissor(this.brbe$scissorLeft + 11, this.brbe$scissorTop + 31, this.brbe$scissorLeft + 136, this.brbe$scissorTop + 156);
        // 视觉当前页滑出（挤压离场）
        brbe$renderVisualSquashed(snap, k, basePage, this.brbe$baseX[k] + Math.round(-frac * PAGE_SLIDE_DISTANCE), this.brbe$baseY[k], gui, x, y, f);
        // 下一页滑入（挤压入场）
        brbe$renderVisualSquashed(snap, k, basePage + 1, this.brbe$baseX[k] + Math.round((1.0F - frac) * PAGE_SLIDE_DISTANCE), this.brbe$baseY[k], gui, x, y, f);
        gui.disableScissor();
        // 已固定配方的 pin 图标绘制在网格 scissor 之外（与静态路径相同的裁剪
        // 状态）：超出配方区的悬出部分不被裁剪。
        for (int[] p : this.brbe$animPinIcons) {
            ClientCompat.blitSprite(gui, BRBTextures.RECIPE_BOOK_PIN_SPRITE, p[0] - 4, p[1] - 4, 32, 32);
        }
    }

    @Unique
    private void brbe$renderVisualSquashed(RecipeButton snap, int k, int page, int x, int y, GuiGraphics gui, int mouseX, int mouseY, float f) {
        int wrapped = brbe$wrapPage(page);
        int idx = wrapped * 20 + k;
        if (idx >= this.recipeCollections.size()) {
            snap.visible = false;
            return;
        }
        ContextMap ctx = SlotDisplayContext.fromLevel(this.minecraft.level);
        snap.init(this.recipeCollections.get(idx), this.isFiltering, (RecipeBookPage) (Object) this, ctx);
        snap.visible = true;
        // 挤压离场：配方滑出视窗边界时，边缘钳制在边界、宽度收窄，直到压成一条线消失
        int leftBound = this.brbe$scissorLeft + 11;
        int rightBound = this.brbe$scissorLeft + 136;
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
        // 动画期间 tooltip 跟随移动配方：记录光标命中的 snapshot 按钮（可见有效区内）
        if (mouseX >= effX && mouseX < effX + effW && mouseY >= y && mouseY < y + 25) {
            this.brbe$animHovered = snap;
        }
        // 格子背景 blit（宽度横向压缩）
        RecipeButtonAccessor acc = (RecipeButtonAccessor) (Object) snap;
        RecipeCollection c = snap.getCollection();
        Identifier sprite;
        if (c.hasCraftable()) {
            sprite = acc.brbe$hasMultipleRecipes() ? acc.brbe$getManyCraftableSprite() : acc.brbe$getCraftableSprite();
        } else {
            sprite = acc.brbe$hasMultipleRecipes() ? acc.brbe$getManyUncraftableSprite() : acc.brbe$getUncraftableSprite();
        }
        int edgeRight = effX + effW;
        RecipeDisplayId currentRecipe;
        try {
            currentRecipe = snap.getCurrentRecipe();
        } catch (ArithmeticException e) {
            currentRecipe = null;
        }
        boolean isPartial = currentRecipe != null && PartialCraftingUtil.isPartiallyCraftable(c, currentRecipe);
        if (effW < 25) {
            // 伪压缩：内容在 [effX, edgeRight] 内裁剪（中间随滑动变短），左右边界 2px
            // 独立渲染（边框完整不缩放）。图标在边界线之后渲染，完整跟随配方滑出视窗。
            // 内容 scissor 右边界收窄 1px：原版 sprite 最右列（col24）顶部 1px 是
            // 透明的，若内容画到该列，滚动时下层配方会从缺口漏出。收窄后缺口处
            // 不绘制任何下层内容，仅显示背景（保持透明效果）。
            gui.enableScissor(effX, y, edgeRight - 1, y + 25);
            gui.blitSprite(ClientCompat.GUI_TEXTURED, sprite, x, y, 25, 25);
            if (isPartial) {
                if (ClientCompat.hasSpriteResource(BRBTextures.RECIPE_BOOK_BUTTON_SLOT_PARTIAL_SPRITE)) {
                    ClientCompat.blitSprite(gui, BRBTextures.RECIPE_BOOK_BUTTON_SLOT_PARTIAL_SPRITE, effX, y, effW, 25);
                } else {
                    gui.fill(effX + 1, y + 1, edgeRight - 1, y + 24, 0x60FF3333);
                }
            }
            gui.disableScissor();
            // 图标（边界线前渲染，边界线将盖住经过的图标）
            brbe$renderItemIcon(snap, gui, x, y);
            // 移动方向的前方边缘盖住后方边缘：配方左移（左端被边界压扁）时左边界
            // 最后渲染（在上层），右移时右边界在上层。
            boolean movingLeft = x < effX;
            if (movingLeft) {
                // 右边界先画（下层）
                gui.enableScissor(Math.max(edgeRight - PageAnimationEdges.right(), effX), y, edgeRight, y + 25);
                gui.blitSprite(ClientCompat.GUI_TEXTURED, sprite, edgeRight - 25, y, 25, 25);
                gui.disableScissor();
                // 左边界最后（上层，盖住右边界）
                gui.enableScissor(effX, y, Math.min(effX + PageAnimationEdges.left(), edgeRight), y + 25);
                gui.blitSprite(ClientCompat.GUI_TEXTURED, sprite, effX, y, 25, 25);
                gui.disableScissor();
            } else {
                // 左边界先画（下层）
                gui.enableScissor(effX, y, Math.min(effX + PageAnimationEdges.left(), edgeRight), y + 25);
                gui.blitSprite(ClientCompat.GUI_TEXTURED, sprite, effX, y, 25, 25);
                gui.disableScissor();
                // 右边界最后（上层，盖住左边界）
                gui.enableScissor(Math.max(edgeRight - PageAnimationEdges.right(), effX), y, edgeRight, y + 25);
                gui.blitSprite(ClientCompat.GUI_TEXTURED, sprite, edgeRight - 25, y, 25, 25);
                gui.disableScissor();
            }
        } else {
            gui.blitSprite(ClientCompat.GUI_TEXTURED, sprite, effX, y, 25, 25);
            if (isPartial) {
                if (ClientCompat.hasSpriteResource(BRBTextures.RECIPE_BOOK_BUTTON_SLOT_PARTIAL_SPRITE)) {
                    ClientCompat.blitSprite(gui, BRBTextures.RECIPE_BOOK_BUTTON_SLOT_PARTIAL_SPRITE, effX, y, 25, 25);
                } else {
                    gui.fill(effX + 1, y + 1, effX + 24, y + 24, 0x60FF3333);
                }
            }
            if (effW > 20) {
                brbe$renderItemIcon(snap, gui, effX, y);
            }
        }
        // 已固定配方：收集 pin 图标位置（最上层，等同原版 RecipeButtonMixin 的
        // RETURN 注入）。动画路径完全不调用 RecipeButton.render
        // （快照按钮是手工渲染），原注入不会运行，且快照按钮的 getX/getY 是陈旧值。
        // 仅收集不绘制：绘制推迟到网格 scissor 关闭之后，pin 超出配方区的悬出部分
        // 才不被裁剪。
        if (BetterRecipeBook.pinnedRecipeManager.has(PinnableRecipeCollection.of(c))) {
            this.brbe$animPinIcons.add(new int[] { effX, y });
        }
    }

    /**
     * 渲染配方图标。多配方且结果完全相同的组（替代配方）复刻原版叠加双渲染：
     * {@code item} 在偏移 5、{@code fakeItem} 在偏移 3，形成叠置效果。
     */
    @Unique
    private void brbe$renderItemIcon(RecipeButton snap, GuiGraphics gui, int baseX, int baseY) {
        ItemStack stack = snap.getDisplayStack();
        RecipeButtonAccessor acc = (RecipeButtonAccessor) (Object) snap;
        int offset = 4;
        if (acc.brbe$hasMultipleRecipes() && acc.brbe$allRecipesHaveSameResultDisplay()) {
            gui.renderItem(stack, baseX + offset + 1, baseY + offset + 1, 0);
            offset = 3;
        }
        gui.renderFakeItem(stack, baseX + offset, baseY + offset);
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
        return BetterRecipeBook.config != null && BetterRecipeBook.config.pageAnimation.pageAnimationEnabled;
    }

    @Unique
    private float brbe$animationDuration() {
        if (BetterRecipeBook.config != null) {
            return BetterRecipeBook.config.pageAnimationDuration;
        }
        return 0.1F;
    }

    /** 大跨度跳转的"跳过中间页"预算时长（秒）：2×配置时长扣除末页指数减速。 */
    @Unique
    private float brbe$bulkBudgetSeconds() {
        float duration = brbe$animationDuration();
        return Math.max(0.05F, (MAX_ANIM_MULTIPLIER - FINAL_DECEL_MULTIPLIER) * duration);
    }
}


package com.alonie.brbe.mixins.scrollablepages;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.mixins.accessors.RecipeButtonAccessor;
import com.alonie.brbe.util.BRBTextures;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.brbe.util.PartialCraftingUtil;
import com.alonie.brbe.util.RecipeBookPageAnimBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
 * {@code RecipeButton.extractRenderState} 调用，动画期间用独立的视觉渲染池
 * （{@code brbe$snapshotButtons}）动态渲染视觉当前页与下一页，scissor 只包住
 * 按钮网格区（不影响页码、箭头、替代配方弹层）。非动画路径完全放行原版。</p>
 *
 * <p>受 {@code brbe.toml} 的 {@code pageAnimation.pageAnimationEnabled}（开关）
 * 与 {@code pageAnimation.pageAnimationDuration}（时长，秒）控制。</p>
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

    @Shadow
    private int currentPage;
    @Shadow
    private List<RecipeButton> buttons;
    @Shadow
    private List<RecipeCollection> recipeCollections;
    @Shadow
    private boolean isFiltering;
    @Shadow
    private Minecraft minecraft;

    @Unique
    private final List<RecipeButton> brbe$snapshotButtons = new ArrayList<>(20);
    @Unique
    private boolean brbe$animActive;
    @Unique
    private boolean brbe$animChase;
    @Unique
    private float brbe$visualPage;
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
        if (Math.abs(this.currentPage - this.brbe$lastRenderedPage) != 1 || !userFlip) {
            // 跨多页跳转（快速连滚）或非用户翻页（恢复浏览记录/配方刷新）：
            // 直接切换，不启动滑动动画
            this.brbe$animActive = false;
            this.brbe$animChase = false;
            this.brbe$visualPage = this.currentPage;
            this.brbe$lastRenderedPage = this.currentPage;
            return;
        }
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

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void brbe$advanceAnimation(GuiGraphicsExtractor gui, int i, int j, int k, int l, float f, CallbackInfo ci) {
        this.brbe$scissorLeft = i;
        this.brbe$scissorTop = j;
        this.brbe$frameCounter++;
        if (!this.brbe$animActive) {
            return;
        }
        float deltaS = f / TICKS_PER_SECOND;
        float remaining = this.currentPage - this.brbe$visualPage;
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
        this.brbe$visualPage += Math.signum(remaining) * move;
    }

    @Redirect(method = "extractRenderState",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeButton;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void brbe$renderButton(RecipeButton button, GuiGraphicsExtractor gui, int x, int y, float f) {
        if (!this.brbe$animActive) {
            // 非动画路径：若按钮位置因动画中途打断而残留偏移，恢复基准
            if (this.brbe$baseX != null) {
                int k = this.buttons.indexOf(button);
                if (k >= 0 && k < this.brbe$baseX.length) {
                    button.setPosition(this.brbe$baseX[k], this.brbe$baseY[k]);
                }
            }
            button.extractRenderState(gui, x, y, f);
            return;
        }
        int k = this.buttons.indexOf(button);
        if (k < 0 || k >= this.brbe$snapshotButtons.size() || this.brbe$baseX == null) {
            button.extractRenderState(gui, x, y, f);
            return;
        }
        int basePage = (int) Math.floor(this.brbe$visualPage);
        float frac = this.brbe$visualPage - basePage;
        RecipeButton snap = this.brbe$snapshotButtons.get(k);
        gui.enableScissor(this.brbe$scissorLeft + 11, this.brbe$scissorTop + 31, this.brbe$scissorLeft + 136, this.brbe$scissorTop + 156);
        // 视觉当前页滑出（挤压离场）
        brbe$renderVisualSquashed(snap, k, basePage, this.brbe$baseX[k] + Math.round(-frac * PAGE_SLIDE_DISTANCE), this.brbe$baseY[k], gui, x, y, f);
        // 下一页滑入（挤压入场）
        brbe$renderVisualSquashed(snap, k, basePage + 1, this.brbe$baseX[k] + Math.round((1.0F - frac) * PAGE_SLIDE_DISTANCE), this.brbe$baseY[k], gui, x, y, f);
        gui.disableScissor();
    }

    @Unique
    private void brbe$renderVisualSquashed(RecipeButton snap, int k, int page, int x, int y, GuiGraphicsExtractor gui, int mouseX, int mouseY, float f) {
        int idx = page * 20 + k;
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
        if ((k + page * 20) % 25 == 0) {
            BetterRecipeBook.LOGGER.info("[BRBE-SQ] page={} k={} x={} effX={} effW={} edgeRight={} y={}",
                    page, k, x, effX, effW, effX + effW, y);
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
            gui.enableScissor(effX, y, edgeRight, y + 25);
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
            gui.fakeItem(snap.getDisplayStack(), x + 4, y + 4);
            // 左边界 2px（上层，盖住图标）
            gui.enableScissor(effX, y, Math.min(effX + 2, edgeRight), y + 25);
            gui.blitSprite(ClientCompat.GUI_TEXTURED, sprite, effX, y, 25, 25);
            gui.disableScissor();
            // 右边界 2px（上层，盖住图标）
            gui.enableScissor(Math.max(edgeRight - 2, effX), y, edgeRight, y + 25);
            gui.blitSprite(ClientCompat.GUI_TEXTURED, sprite, edgeRight - 25, y, 25, 25);
            gui.disableScissor();
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
                gui.fakeItem(snap.getDisplayStack(), effX + 4, y + 4);
            }
        }
    }

    @Unique
    private boolean brbe$animationEnabled() {
        return BetterRecipeBook.config != null && BetterRecipeBook.config.pageAnimation.pageAnimationEnabled;
    }

    @Unique
    private float brbe$animationDuration() {
        if (BetterRecipeBook.config != null) {
            return BetterRecipeBook.config.pageAnimation.pageAnimationDuration;
        }
        return 0.1F;
    }
}

package com.alonie.brbe.mixins.scrollablepages;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.RecipeBookPageAnimBridge;
import com.alonie.brbe.util.PageFlipDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.21.1 简化版翻页动画（快照池）。
 *
 * <p>对照 1.21.11 的 RecipeBookPageAnimationMixin：1.21.1 的 RecipeButton 用
 * 无参构造 + {@code init(RecipeCollection, RecipeBookPage)}（2 参），无
 * SlotSelectTime/RecipeDisplayId 体系——快照池与动画核心按 1.21.1 API 重写。
 * 视觉：用户翻页时旧页按钮向右滑出、新页按钮从右滑入（挤压离场/入场），
 * scissor 只包住按钮网格区；配置 pageAnimation.pageAnimationEnabled /
 * pageAnimationDuration 控制。</p>
 */
@Mixin(RecipeBookPage.class)
public abstract class RecipeBookPageAnimationMixin {

    private static final int PAGE_SLIDE_DISTANCE = 125;
    private static final float SNAP_THRESHOLD = 0.002F;

    @Shadow private int currentPage;
    @Shadow private int totalPages;
    @Shadow private List<RecipeButton> buttons;
    @Shadow private List<RecipeCollection> recipeCollections;
    @Shadow private Minecraft minecraft;

    @Unique private final List<RecipeButton> brbe$snapshotButtons = new ArrayList<>(20);
    @Unique private boolean brbe$animActive;
    @Unique private boolean brbe$animBackward;
    @Unique private boolean brbe$animChase;
    @Unique private float brbe$visualPage;
    @Unique private float brbe$floatTarget;
    @Unique private int brbe$lastRenderedPage = -1;
    @Unique private int[] brbe$baseX;
    @Unique private int[] brbe$baseY;
    @Unique private int brbe$gridLeft;
    @Unique private int brbe$gridRight;
    @Unique private int brbe$gridTop;
    @Unique private int brbe$gridBottom;
    @Unique private int brbe$animMouseX;
    @Unique private int brbe$animMouseY;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void brbe$initSnapshotButtons(CallbackInfo ci) {
        for (int i = 0; i < 20; i++) {
            this.brbe$snapshotButtons.add(new RecipeButton());
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void brbe$captureGridBounds(GuiGraphics gui, int areaLeft, int areaTop,
                                        int areaWidth, int areaHeight, float f, CallbackInfo ci) {
        this.brbe$gridLeft = areaLeft;
        this.brbe$gridTop = areaTop;
        this.brbe$gridRight = areaLeft + areaWidth;
        this.brbe$gridBottom = areaTop + areaHeight;
        this.brbe$animMouseX = this.minecraft != null && this.minecraft.mouseHandler != null
                ? (int) this.minecraft.mouseHandler.xpos() : areaLeft;
        this.brbe$animMouseY = this.minecraft != null && this.minecraft.mouseHandler != null
                ? (int) this.minecraft.mouseHandler.ypos() : areaTop;
    }

    /** 用户翻页检测（updateButtonsForPage HEAD：此处 currentPage 已变）。 */
    @Inject(method = "updateButtonsForPage", at = @At("HEAD"))
    private void brbe$detectFlip(CallbackInfo ci) {
        boolean userFlip = RecipeBookPageAnimBridge.consumeUserFlip();
        if (!brbe$animationEnabled()) {
            this.brbe$animActive = false;
            this.brbe$lastRenderedPage = this.currentPage;
            return;
        }
        if (this.currentPage == this.brbe$lastRenderedPage) return;
        if (!userFlip) {
            // 非用户翻页（恢复记录/配方刷新）：直接切换
            this.brbe$animActive = false;
            this.brbe$visualPage = this.currentPage;
            this.brbe$lastRenderedPage = this.currentPage;
            return;
        }
        this.brbe$animBackward = PageFlipDirection.backward(
                Math.round(this.brbe$visualPage), this.currentPage, this.totalPages,
                BetterRecipeBook.config != null && BetterRecipeBook.config.scrolling.scrollAround);
        this.brbe$floatTarget = this.currentPage;
        if (!this.brbe$animActive) {
            this.brbe$baseX = new int[this.buttons.size()];
            this.brbe$baseY = new int[this.buttons.size()];
            for (int k = 0; k < this.buttons.size(); k++) {
                this.brbe$baseX[k] = this.buttons.get(k).getX();
                this.brbe$baseY[k] = this.buttons.get(k).getY();
            }
        }
        this.brbe$animActive = true;
        this.brbe$lastRenderedPage = this.currentPage;
    }

    /** 渲染：动画期间平移原按钮 + 旧页快照滑出。 */
    @Inject(method = "render", at = @At("RETURN"))
    private void brbe$animate(GuiGraphics gui, int areaLeft, int areaTop,
                              int areaWidth, int areaHeight, float f, CallbackInfo ci) {
        int mouseX = this.brbe$animMouseX;
        int mouseY = this.brbe$animMouseY;
        if (!this.brbe$animActive) return;

        float deltaS = f / 20.0F;
        float remaining = this.brbe$floatTarget - this.brbe$visualPage;
        float absd = Math.abs(remaining);
        if (absd < SNAP_THRESHOLD) {
            this.brbe$visualPage = this.currentPage;
            this.brbe$animActive = false;
            this.brbe$animChase = false;
            this.brbe$animBackward = false;
            return;
        }
        float base = this.brbe$animChase ? 6.2F / 0.25F : 6.2F / brbe$animationDuration();
        float fraction = 1.0F - (float) Math.exp(-base * deltaS);
        float cap = 0.45F + (float) Math.sqrt(absd) * 0.12F;
        float move = Math.min(Math.min(absd * fraction, cap), absd);
        this.brbe$visualPage += Math.signum(remaining) * move;

        int basePage = (int) Math.floor(this.brbe$visualPage);
        float frac = this.brbe$visualPage - basePage;
        int offset = this.brbe$animBackward
                ? Math.round((1.0F - frac) * PAGE_SLIDE_DISTANCE)
                : Math.round(-frac * PAGE_SLIDE_DISTANCE);

        // 平移当前页按钮（新页从右滑入）
        for (int k = 0; k < this.buttons.size(); k++) {
            if (k >= this.brbe$baseX.length) break;
            RecipeButton btn = this.buttons.get(k);
            btn.setPosition(this.brbe$baseX[k] + offset, this.brbe$baseY[k]);
        }

        // 快照旧页滑出（用快照池绑定前一页，平移渲染）
        int prevPage = this.brbe$animBackward ? basePage + 1 : basePage - 1;
        int wrapPrev = brbe$wrapPage(prevPage);
        int prevOffset = this.brbe$animBackward
                ? Math.round(frac * -PAGE_SLIDE_DISTANCE)
                : Math.round((1.0F - frac) * PAGE_SLIDE_DISTANCE);
        gui.enableScissor(this.brbe$gridLeft + 11, this.brbe$gridTop + 31,
                this.brbe$gridRight, this.brbe$gridBottom);
        for (int k = 0; k < 20; k++) {
            int idx = wrapPrev * 20 + k;
            if (idx >= this.recipeCollections.size()) break;
            RecipeButton snap = this.brbe$snapshotButtons.get(k);
            snap.init(this.recipeCollections.get(idx), (RecipeBookPage) (Object) this);
            snap.setPosition((this.brbe$baseX != null && k < this.brbe$baseX.length
                    ? this.brbe$baseX[k] : 0) + prevOffset,
                    (this.brbe$baseY != null && k < this.brbe$baseY.length
                    ? this.brbe$baseY[k] : 0));
            snap.render(gui, mouseX, mouseY, f);
        }
        gui.disableScissor();
        this.brbe$animChase = true;
    }

    @Unique
    private int brbe$wrapPage(int page) {
        if (page < 0) return page + this.totalPages;
        if (page >= this.totalPages) return page - this.totalPages;
        return page;
    }

    @Unique
    private boolean brbe$animationEnabled() {
        return BetterRecipeBook.config != null
                && BetterRecipeBook.config.pageAnimation != null
                && BetterRecipeBook.config.pageAnimation.pageAnimationEnabled;
    }

    @Unique
    private float brbe$animationDuration() {
        return Math.max(0.05F, BetterRecipeBook.config.pageAnimationDuration);
    }
}

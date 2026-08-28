package com.alonie.brbe.mixins.scrollablepages;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.PageFlipDirection;
import com.alonie.brbe.util.RecipeBookPageAnimBridge;
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.21.1 简化版翻页动画（快照池 + @Redirect 渲染）。
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
 * <p>视觉：用户翻页时视觉当前页向左滑出、下一页从右滑入（简单平移视效，
 * 与 1.21.11 完整版挤压视效的已知降级保持一致）；scissor 只包住按钮网格区
 * （不影响页码、箭头、替代配方弹层）；tooltip 跟随光标命中的快照按钮。</p>
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
    /** 网格 scissor 原点（render 的 areaLeft/areaTop 参数，即页面背景左上角）。 */
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
        int boundLeft = this.brbe$scissorLeft + 11;
        int boundTop = this.brbe$scissorTop + 31;
        int boundRight = this.brbe$scissorLeft + 136;
        int boundBottom = this.brbe$scissorTop + 131;
        gui.enableScissor(boundLeft, boundTop, boundRight, boundBottom);
        // 视觉当前页向左滑出 + 下一页从右滑入（简单平移视效）
        brbe$renderVisual(snap, k, basePage,
                button.getX() + Math.round(-frac * PAGE_SLIDE_DISTANCE), button.getY(),
                gui, mouseX, mouseY, delta);
        brbe$renderVisual(snapIn, k, basePage + 1,
                button.getX() + Math.round((1.0F - frac) * PAGE_SLIDE_DISTANCE), button.getY(),
                gui, mouseX, mouseY, delta);
        gui.disableScissor();
    }

    @Unique
    private void brbe$renderVisual(RecipeButton snap, int k, int page, int x, int y,
                                   GuiGraphics gui, int mouseX, int mouseY, float delta) {
        int wrapped = brbe$wrapPage(page);
        int idx = wrapped * 20 + k;
        if (idx >= this.recipeCollections.size()) {
            snap.visible = false;
            return;
        }
        snap.init(this.recipeCollections.get(idx), (RecipeBookPage) (Object) this);
        snap.visible = true;
        snap.setPosition(x, y);
        // 动画期间 tooltip 跟随移动配方：记录网格有效区内光标命中的快照按钮
        if (mouseX >= x && mouseX < x + 25 && mouseY >= y && mouseY < y + 25
                && mouseX >= this.brbe$scissorLeft + 11 && mouseX < this.brbe$scissorLeft + 136
                && mouseY >= this.brbe$scissorTop + 31 && mouseY < this.brbe$scissorTop + 131) {
            this.brbe$animHovered = snap;
        }
        snap.render(gui, mouseX, mouseY, delta);
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

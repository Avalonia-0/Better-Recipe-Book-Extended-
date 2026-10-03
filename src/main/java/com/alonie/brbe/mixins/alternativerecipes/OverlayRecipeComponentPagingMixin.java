package com.alonie.brbe.mixins.alternativerecipes;

import com.alonie.brbe.mixins.accessors.OverlayRecipeComponentAccessor;
import com.alonie.brbe.util.AlternativeOverlayLayout;
import com.alonie.brbe.util.AlternativesPaging;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.brbe.util.LeiPageButtons;
import com.alonie.brbe.util.RecipeViewerOverlay;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * **原版替代配方组浮层的分页**（用户 2026-09-25 诉求）：列上限 4、行上限 4 —— 每页 ≤16 条，
 * 超出部分翻页查看；翻页键复用 LEI 查询窗口那对（位置、贴图、音效、Ctrl 跳首/末页都一致），
 * 角标按用户要求"裁切中心三角后交换"成 **左 ▲ / 右 ▼**（见 {@link LeiPageButtons}）。
 *
 * <p>原版自己不限制行数（{@code size<=16 ? 4 : 5} 列、行数 = ceil(size/列)），大组会顶出屏幕。
 * 这里的做法：{@code init} 尾部把原版建好的按钮**全量留档**，只把当前页的 16 个放回原版自己的
 * {@code recipeButtons} 列表并重排到 4 列网格——于是原版后续的背景尺寸、命中、渲染全部照旧
 * 按"这一页的条目数"工作（≤16 时原版本来就按 4 列算），我们只需补面板上方的标题带与翻页键。</p>
 *
 * <p>BRBE 查询窗口自己的网格浮层也用 {@code OverlayRecipeComponent}（每页一个 collection），
 * 它的布局/渲染由 {@code RecipeViewerOverlay} 全权接管——这里一律跳过（
 * {@link RecipeViewerOverlay#isOwnOverlay}）。</p>
 */
@Mixin(OverlayRecipeComponent.class)
public abstract class OverlayRecipeComponentPagingMixin implements AlternativesPaging.Target {

    /** 每页上限：4 列 × 4 行。 */
    @Unique
    private static final int brbe$PAGE_COLUMNS = 4;
    @Unique
    private static final int brbe$PAGE_ROWS = 4;
    @Unique
    private static final int brbe$PER_PAGE = brbe$PAGE_COLUMNS * brbe$PAGE_ROWS;
    /** 配方格步距（原版布局 25px）。 */
    @Unique
    private static final int brbe$CELL = 25;

    /** 原版建好的全部按钮（分页切片用；只在 {@code init} 尾部填一次）。 */
    @Unique
    private final List<AbstractWidget> brbe$allButtons = new ArrayList<>();
    @Unique
    private int brbe$page;
    @Unique
    private int brbe$pageCount = 1;

    @Inject(method = "init", at = @At("TAIL"))
    private void brbe$setupPaging(RecipeCollection collection, ContextMap contextMap, boolean bl,
                                  int i, int j, int k, int l, float f, CallbackInfo ci) {
        if (RecipeViewerOverlay.isOwnOverlay((OverlayRecipeComponent) (Object) this)) {
            return;
        }
        OverlayRecipeComponentAccessor accessor = (OverlayRecipeComponentAccessor) (Object) this;
        this.brbe$allButtons.clear();
        this.brbe$allButtons.addAll(accessor.getRecipeButtons());
        this.brbe$page = 0;
        this.brbe$pageCount = Math.max(1, (this.brbe$allButtons.size() + brbe$PER_PAGE - 1) / brbe$PER_PAGE);
        if (this.brbe$pageCount > 1) {
            // 位置必须在按钮排布**之前**算好（applyPage 把 x/y 烘进按钮坐标）
            this.brbe$placePagedBox(i, j, k, l, f);
        }
        this.brbe$applyPage();
    }

    /**
     * 分页盒的**坐标**：按"这一页"的几何重算（原版规则，见 {@link AlternativeOverlayLayout#placeBox}）。
     *
     * <p>为什么必须重算（用户 2026-09-26 反馈："分页的替代配方组总是生成在同一个位置，
     * 而不是像普通的替代配方组随鼠标的位置生成"）：原版的 x/y 是按**整组**条目数算的——
     * 列数 = {@code size<=16 ? 4 : 5}，行数 = {@code ceil(size/columns)}。一个 45 条的组
     * 于是被当成 **5 列 × 9 行**：第二步把盒底往上顶到配方格外（{@code y ≈ 面板顶 - 19}），
     * 那个坐标再经"上屏夹取"就固定成**配方书上方的一个点**，与点的是哪个组按钮、哪一行都无关
     * （离线复算：640×360 缩放下恒为 {@code (171, 78)}，而配方格从 {@code (171, 128)} 起）。
     * 分页后盒子实际只有 4 列 × ≤4 行，位置就该按本页几何算——这样它的落点与"原版里一个
     * 13~16 条的普通组"完全同规则。</p>
     *
     * <p>注入点在同为 {@code init} 末端的 {@code OverlayRecipeComponentPositionMixin#brbe$keepOverlayOnScreen}
     * 之前或之后都安全：夹取是幂等的，谁后跑结果都一致。</p>
     */
    @Unique
    private void brbe$placePagedBox(int anchorX, int anchorY, int centerX, int centerY, float cell) {
        Minecraft minecraft = Minecraft.getInstance();
        int[] box = AlternativeOverlayLayout.placeBox(anchorX, anchorY, brbe$PER_PAGE, brbe$PAGE_COLUMNS,
                brbe$boxWidth(), brbe$PAGE_ROWS * brbe$CELL + 8, centerX, centerY, cell,
                minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight());
        OverlayRecipeComponentAccessor accessor = (OverlayRecipeComponentAccessor) (Object) this;
        accessor.setX(box[0]);
        accessor.setY(box[1]);
    }

    /** 只把当前页的按钮放回原版列表，并重排成 4 列网格（分页时盒宽恒定，翻页键才不跳）。 */
    @Unique
    private void brbe$applyPage() {
        OverlayRecipeComponentAccessor accessor = (OverlayRecipeComponentAccessor) (Object) this;
        List<AbstractWidget> live = accessor.getRecipeButtons();
        live.clear();
        int start = this.brbe$page * brbe$PER_PAGE;
        int end = Math.min(this.brbe$allButtons.size(), start + brbe$PER_PAGE);
        for (int index = start; index < end; ++index) {
            AbstractWidget button = this.brbe$allButtons.get(index);
            int slot = index - start;
            button.setPosition(accessor.getX() + 4 + brbe$CELL * (slot % brbe$PAGE_COLUMNS),
                    accessor.getY() + 5 + brbe$CELL * (slot / brbe$PAGE_COLUMNS));
            live.add(button);
        }
    }

    /** 这个浮层是否由本 Mixin 分页（BRBE 查询窗口自己的网格浮层不参与；隐藏后也不画/不认领）。 */
    @Unique
    private boolean brbe$paged() {
        return ((OverlayRecipeComponent) (Object) this).isVisible()
                && this.brbe$pageCount > 1
                && !RecipeViewerOverlay.isOwnOverlay((OverlayRecipeComponent) (Object) this);
    }

    /** 分页时的盒宽（固定 4 列：翻页键右对齐才不随每页条目数漂）。 */
    @Unique
    private static int brbe$boxWidth() {
        return brbe$PAGE_COLUMNS * brbe$CELL + 8;
    }

    /** 这一页的行数 → 盒高（末页条目少就矮）。 */
    @Unique
    private int brbe$boxHeight() {
        int pageItems = Math.min(brbe$PER_PAGE, this.brbe$allButtons.size() - this.brbe$page * brbe$PER_PAGE);
        int rows = Math.max(1, (Math.max(0, pageItems) + brbe$PAGE_COLUMNS - 1) / brbe$PAGE_COLUMNS);
        return Math.min(rows, brbe$PAGE_ROWS) * brbe$CELL + 8;
    }

    // ── 独立滚轮翻页区（AlternativesPaging.Target，用户 2026-09-26）──────────────────

    /**
     * 浮层渲染时自登记（= 逐帧续心跳，见 {@link AlternativesPaging} 的"登记必须是活的"），
     * 滚轮判定在 {@code RecipeBookGesture} 的最前面。
     */
    @Unique
    private void brbe$trackScrollRegion() {
        if (this.brbe$paged()) {
            AlternativesPaging.track((AlternativesPaging.Target) (Object) this);
        }
    }

    @Override
    public boolean paged() {
        return this.brbe$paged();
    }

    @Override
    public boolean inScrollRegion(int mouseX, int mouseY) {
        OverlayRecipeComponent self = (OverlayRecipeComponent) (Object) this;
        if (!self.isVisible()) {
            return false;
        }
        OverlayRecipeComponentAccessor accessor = (OverlayRecipeComponentAccessor) self;
        if (mouseX >= accessor.getX() && mouseX < accessor.getX() + brbe$boxWidth()
                && mouseY >= accessor.getY() && mouseY < accessor.getY() + this.brbe$boxHeight()) {
            return true;
        }
        int leftX = LeiPageButtons.leftX(accessor.getX(), brbe$boxWidth());
        int topY = LeiPageButtons.topY(accessor.getY());
        return LeiPageButtons.overPrev(leftX, topY, mouseX, mouseY)
                || LeiPageButtons.overNext(leftX, topY, mouseX, mouseY);
    }

    @Override
    public void flipPage(double verticalAmount) {
        int next = AlternativesPaging.stepPage(this.brbe$page, this.brbe$pageCount, verticalAmount);
        if (next == this.brbe$page) {
            return;
        }
        this.brbe$page = next;
        // 翻页不是"点配方"：清掉上次点击的配方，避免调用方拿旧配方再放一次
        ((OverlayRecipeComponentAccessor) (Object) this).brbe$setLastRecipeClicked(null);
        ClientCompat.playPageFlipSound(Minecraft.getInstance());
        this.brbe$applyPage();
    }

    /**
     * 分页时把面板背景**加宽到固定 4 列**（原版按"这一页的条目数"算宽度：最后一页条目少就变窄，
     * 右对齐的翻页键会跟着漂）。注意**不向上拓展**——用户 2026-09-26：翻页键悬浮即可。
     */
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void brbe$fixedPanelWidth(GuiGraphics gui, RenderPipeline pipeline, Identifier sprite,
                                      int bx, int by, int bw, int bh) {
        if (!this.brbe$paged()) {
            gui.blitSprite(pipeline, sprite, bx, by, bw, bh);
            return;
        }
        gui.blitSprite(pipeline, sprite, bx, by, brbe$boxWidth(), bh);
    }

    /** 画一对翻页键（**悬浮**在面板上方、右对齐；不拓展面板背景——用户 2026-09-26）。 */
    @Inject(method = "render", at = @At("RETURN"))
    private void brbe$drawPageButtons(GuiGraphics gui, int mouseX, int mouseY, float delta,
                                      CallbackInfo ci) {
        if (!this.brbe$paged()) {
            return;
        }
        this.brbe$trackScrollRegion();
        OverlayRecipeComponentAccessor accessor = (OverlayRecipeComponentAccessor) (Object) this;
        LeiPageButtons.draw(gui, LeiPageButtons.leftX(accessor.getX(), brbe$boxWidth()),
                LeiPageButtons.topY(accessor.getY()),
                mouseX, mouseY, this.brbe$page > 0, this.brbe$page < this.brbe$pageCount - 1,
                this.brbe$page, this.brbe$pageCount);
    }

    /** 翻页键点击：翻页 + 翻页音效；Ctrl+点击跳首页/末页（与 LEI 查询窗口同语义）。 */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void brbe$pageButtonClick(MouseButtonEvent event, boolean bl, CallbackInfoReturnable<Boolean> cir) {
        if (!this.brbe$paged() || !ClientCompat.isLeftClick(event)) {
            return;
        }
        OverlayRecipeComponentAccessor accessor = (OverlayRecipeComponentAccessor) (Object) this;
        int boxWidth = brbe$PAGE_COLUMNS * brbe$CELL + 8;
        int leftX = LeiPageButtons.leftX(accessor.getX(), boxWidth);
        int topY = LeiPageButtons.topY(accessor.getY());
        int mx = Mth.floor(event.x());
        int my = Mth.floor(event.y());
        boolean overPrev = LeiPageButtons.overPrev(leftX, topY, mx, my);
        boolean overNext = LeiPageButtons.overNext(leftX, topY, mx, my);
        if (!overPrev && !overNext) {
            return;
        }
        // 命中翻页键 = "不是点配方"：清掉上次点击的配方——页/组件把 mouseClicked 的 true
        // 当成"有配方要放置"（RecipeBookPage → lastClickedRecipe → tryPlaceRecipe），
        // 不清就会拿旧配方再放一次。
        accessor.brbe$setLastRecipeClicked(null);
        boolean prev = overPrev && this.brbe$page > 0;
        boolean next = overNext && this.brbe$page < this.brbe$pageCount - 1;
        if (prev || next) {
            this.brbe$page = ClientCompat.isControlDown()
                    ? (prev ? 0 : this.brbe$pageCount - 1)
                    : (prev ? this.brbe$page - 1 : this.brbe$page + 1);
            ClientCompat.playPageFlipSound(Minecraft.getInstance());
            this.brbe$applyPage();
        }
        cir.setReturnValue(true); // 吞掉点击（浮层不关）
    }
}

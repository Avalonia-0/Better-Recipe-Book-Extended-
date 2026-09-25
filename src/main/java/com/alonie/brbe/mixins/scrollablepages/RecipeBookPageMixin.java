package com.alonie.brbe.mixins.scrollablepages;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.brbe.util.CycleLock;
import com.alonie.brbe.util.RecipeBookPageAnimBridge;
import com.alonie.brbe.util.RecipeViewerOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipeBookPage.class)
public abstract class RecipeBookPageMixin {
    /** 翻页音效上次播放时间（毫秒），用于 0.1s 播放间隔节流。 */
    @Unique
    private static long brbe$lastPageFlipSoundTime;

    @Shadow
    private int currentPage;
    @Shadow
    private int totalPages;

    @Shadow
    protected abstract void updateButtonsForPage();

    @Shadow
    private ImageButton forwardButton;
    @Shadow
    private ImageButton backButton;

    @Shadow
    private RecipeDisplayId lastClickedRecipe;
    @Shadow
    private RecipeCollection lastClickedRecipeCollection;

    @Shadow
    private net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent overlay;

    /**
     * 翻页（点击/滚轮/updateCollections 等所有路径都会走到
     * updateButtonsForPage）后关闭替代配方 overlay，否则它会留在原地。
     * 对 BRBE R/U viewer：仅当 viewer 是从配方书内的配方打开（R/U 作用于
     * 配方书按钮）时才在翻页时关闭它；从容器/幽灵物品打开的 viewer 不受影响。
     *
     * <p>2026-09-01 多窗口审查：`this.overlay` 是**宿主配方书页面自己的**替代配方
     * overlay（与 BRBE viewer 的 OverlayRecipeComponent 实例无关），viewer 激活时
     * 也不该把它排除在关闭之外——它必须随翻页关闭，否则留在原地常驻渲染
     * （即"容器 UI 飞走"的挂死半截界面）。原 `isViewerActive` 分支直接 return
     * 跳过对宿主 overlay 的 setVisible(false)，是单窗口时代的误判。现在宿主
     * overlay 无条件关闭；book 打开 viewer 的窗口（如果有）仍按原语义关闭。
     */
    @Inject(method = "updateButtonsForPage", at = @At("RETURN"))
    private void brbe$closeOverlayOnPageChange(CallbackInfo ci) {
        if (com.alonie.brbe.cache.RecipeViewerIndex.isViewerActive()) {
            if (com.alonie.brbe.cache.RecipeViewerIndex.isViewerOpenedFromBook()) {
                com.alonie.brbe.util.RecipeViewerOverlay.close();
            }
            this.overlay.setVisible(false);
            return;
        }
        this.overlay.setVisible(false);
    }

    /**
     * While the BRBE R/U viewer overlay is up, the recipe book is locked to its
     * current page: the turn-page buttons must not flip the page.  Redirecting
     * (instead of cancelling the whole method) lets the page reset its
     * lastClickedRecipe/lastClickedRecipeCollection, so the recipe-book
     * component does not try to place a stale recipe on this click.
     */
    @Redirect(method = "mouseClicked",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/gui/components/ImageButton;mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z"),
              require = 2)
    private boolean brbe$blockPageTurnWhileViewer(ImageButton button, MouseButtonEvent event, boolean doubleClick) {
        // 桌面窗口语义：只有光标落在查询界面/pin/预览（query UI 拥有的点）时
        // 才锁定配方书翻页；窗口之外翻页照常。
        if (RecipeViewerOverlay.modalMaskOwnsCursor((int) Mth.floor(event.x()), (int) Mth.floor(event.y()))) return false;
        boolean clicked = button.mouseClicked(event, doubleClick);
        // 命中翻页箭头 = 用户主动翻页，标记以触发动画
        if (clicked && (button == forwardButton || button == backButton)) {
            RecipeBookPageAnimBridge.markUserFlip();
        }
        return clicked;
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    public void mouseClickedBtn(MouseButtonEvent event, int areaLeft, int areaTop, int areaWidth, int areaHeight, boolean widthTooNarrow, CallbackInfoReturnable<Boolean> cir) {
        if (RecipeViewerOverlay.modalMaskOwnsCursor((int) Mth.floor(event.x()), (int) Mth.floor(event.y()))) {
            return;
        }

        if (!BetterRecipeBook.config.scrolling.scrollAround || totalPages <= 1 || !ClientCompat.isLeftClick(event)) {
            return;
        }

        if (currentPage == totalPages - 1 && ClientCompat.mouseClicked(forwardButton, event.x(), event.y(), event.button())) {
            // HEAD 拦截绕过了原方法开头的 lastClickedRecipe 重置，必须手动清空，
            // 否则外层 handlePlaceRecipe 会把上一次点击的配方误放置到合成格。
            this.lastClickedRecipe = null;
            this.lastClickedRecipeCollection = null;
            RecipeBookPageAnimBridge.markUserFlip();
            currentPage = 0;
            updateButtonsForPage();
            cir.setReturnValue(true);
            return;
        }

        if (currentPage == 0 && ClientCompat.mouseClicked(backButton, event.x(), event.y(), event.button())) {
            this.lastClickedRecipe = null;
            this.lastClickedRecipeCollection = null;
            RecipeBookPageAnimBridge.markUserFlip();
            currentPage = totalPages - 1;
            updateButtonsForPage();
            cir.setReturnValue(true);
        }
    }

    /**
     * Ctrl+点击翻页箭头直接跳到首页/尾页（跨多页由动画 mixin 自动降级为直接切换）。
     * HEAD 拦截，优先于原版逐页翻页逻辑；配方查看器激活时不介入。
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    public void brbe$mouseClickedJumpToEdge(MouseButtonEvent event, int areaLeft, int areaTop, int areaWidth, int areaHeight, boolean widthTooNarrow, CallbackInfoReturnable<Boolean> cir) {
        if (RecipeViewerOverlay.modalMaskOwnsCursor((int) Mth.floor(event.x()), (int) Mth.floor(event.y()))
                || !ClientCompat.isLeftClick(event) || !ClientCompat.isControlDown()) {
            return;
        }

        if (ClientCompat.mouseClicked(forwardButton, event.x(), event.y(), event.button())) {
            // 与原版 mouseClicked 开头一致：清空上次点击，避免外层误放置旧配方
            this.lastClickedRecipe = null;
            this.lastClickedRecipeCollection = null;
            if (currentPage < totalPages - 1) {
                RecipeBookPageAnimBridge.markUserFlip();
                currentPage = totalPages - 1;
                updateButtonsForPage();
            }
            cir.setReturnValue(true);
            return;
        }

        if (ClientCompat.mouseClicked(backButton, event.x(), event.y(), event.button())) {
            this.lastClickedRecipe = null;
            this.lastClickedRecipeCollection = null;
            if (currentPage > 0) {
                RecipeBookPageAnimBridge.markUserFlip();
                currentPage = 0;
                updateButtonsForPage();
            }
            cir.setReturnValue(true);
        }
    }

    @Inject(at = @At("RETURN"), method = "extractRenderState")
    public void extractRenderState(GuiGraphicsExtractor gui, int i, int j, int k, int l, float f, CallbackInfo ci) {
        // 桌面窗口语义：只有光标落在查询界面/pin/预览区域时配方书才锁定翻页
        // （吞掉滚轮）；窗口之外滚轮照常翻页。
        if (RecipeViewerOverlay.modalMaskOwnsCursor(k, l)) {
            BetterRecipeBook.queuedScroll = 0;
            return;
        }

        // 「锁定折叠物品」键按住时滚轮改为**逐格翻动指针下那一件折叠物品**（配方书
        // 网格按钮的配方图标 / 功能方块里的幽灵物品），不翻页（用户 2026-09-13
        // 诉求 1+2）。判定放在这里而不是 MouseScrollHandler：本方法每帧都跑、又能
        // 拿到光标，且上面的 modalMaskOwnsCursor 已经把「光标在查询界面/pin/预览上」
        // 的情形排除掉了（那里由查询窗口自己处理滚轮）。没有物品被指着时不消费
        // 滚轮，照常翻页。
        if (BetterRecipeBook.queuedScroll != 0 && CycleLock.isDown()
                && CycleLock.step(BetterRecipeBook.queuedScroll)) {
            BetterRecipeBook.queuedScroll = 0;
            return;
        }

        if (BetterRecipeBook.queuedScroll != 0 && true) {
            if (isMouseOverRecipeBookPage(k, l, i, j) && totalPages > 1) {
                RecipeBookPageAnimBridge.markUserFlip();
                int oldPage = currentPage;
                currentPage += BetterRecipeBook.queuedScroll;
                if (currentPage >= totalPages) {
                    currentPage = BetterRecipeBook.config.scrolling.scrollAround ? currentPage % totalPages : totalPages - 1;
                } else if (currentPage < 0) {
                    // required as % is not modulus, it is remainder. we need to force output positive by((currentPage % totalPages) + totalPages)
                    currentPage = BetterRecipeBook.config.scrolling.scrollAround ? (currentPage % totalPages) + totalPages : 0;
                }

                // Only play the sound when the page actually changed (scrolling
                // past the first/last page without scroll-around is silent).
                if (currentPage != oldPage && BetterRecipeBook.config.scrollPageSound) {
                    // 0.01s 播放间隔节流，避免快速滚动时音效过密。
                    long now = Util.getMillis();
                    if (now - brbe$lastPageFlipSoundTime >= 10) {
                        brbe$lastPageFlipSoundTime = now;
                        // 与其余翻页界面走同一条路径：音效 ID 取配置（ClientCompat →
                        // PageFlipSound）、音量 0.25 x pageFlipVolume。
                        ClientCompat.playPageFlipSound(Minecraft.getInstance());
                    }
                }
                updateButtonsForPage();
            }
            BetterRecipeBook.queuedScroll = 0;
        }
    }

    private static boolean isMouseOverRecipeBookPage(int mouseX, int mouseY, int left, int top) {
        return mouseX >= left && mouseX < left + 147 && mouseY >= top && mouseY < top + 166;
    }

    @Inject(at = @At("RETURN"), method = "init")
    public void init(Minecraft minecraftClient, int parentLeft, int parentTop, CallbackInfo ci) {
        BetterRecipeBook.queuedScroll = 0;
    }

    @Inject(method = "updateArrowButtons", at = @At("RETURN"))
    private void updateArrowButtons(CallbackInfo ci) {
        if (BetterRecipeBook.config.scrolling.scrollAround && totalPages > 1) {
            forwardButton.visible = true;
            backButton.visible = true;
            forwardButton.active = true;
            backButton.active = true;
        }
    }
}

package com.alonie.brbe.mixins.scrollablepages;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.cache.RecipeViewerIndex;
import com.alonie.brbe.util.ClientCompat;
import com.alonie.brbe.util.RecipeBookPageAnimBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
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
    private net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent overlay;

    /**
     * 翻页（点击/滚轮/updateCollections 等所有路径都会走到
     * updateButtonsForPage）后关闭替代配方 overlay，否则它会留在原地。
     * 对 BRBE R/U viewer：仅当 viewer 是从配方书内的配方打开（R/U 作用于
     * 配方书按钮）时才在翻页时关闭它；从容器/幽灵物品打开的 viewer 不受影响。
     */
    @Inject(method = "updateButtonsForPage", at = @At("RETURN"))
    private void brbe$closeOverlayOnPageChange(CallbackInfo ci) {
        if (com.alonie.brbe.cache.RecipeViewerIndex.isViewerActive()) {
            if (com.alonie.brbe.cache.RecipeViewerIndex.isViewerOpenedFromBook()) {
                com.alonie.brbe.util.RecipeViewerOverlay.close();
            }
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
        if (RecipeViewerIndex.isViewerActive()) return false;
        boolean clicked = button.mouseClicked(event, doubleClick);
        // 命中翻页箭头 = 用户主动翻页，标记以触发动画
        if (clicked && (button == forwardButton || button == backButton)) {
            RecipeBookPageAnimBridge.markUserFlip();
        }
        return clicked;
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    public void mouseClickedBtn(MouseButtonEvent event, int areaLeft, int areaTop, int areaWidth, int areaHeight, boolean widthTooNarrow, CallbackInfoReturnable<Boolean> cir) {
        if (RecipeViewerIndex.isViewerActive()) {
            return;
        }

        if (!BetterRecipeBook.config.scrolling.scrollAround || totalPages <= 1 || event.button() != 0) {
            return;
        }

        if (currentPage == totalPages - 1 && ClientCompat.mouseClicked(forwardButton, event.x(), event.y(), event.button())) {
            RecipeBookPageAnimBridge.markUserFlip();
            currentPage = 0;
            updateButtonsForPage();
            cir.setReturnValue(true);
            return;
        }

        if (currentPage == 0 && ClientCompat.mouseClicked(backButton, event.x(), event.y(), event.button())) {
            RecipeBookPageAnimBridge.markUserFlip();
            currentPage = totalPages - 1;
            updateButtonsForPage();
            cir.setReturnValue(true);
        }
    }

    @Inject(at = @At("RETURN"), method = "extractRenderState")
    public void extractRenderState(GuiGraphicsExtractor gui, int i, int j, int k, int l, float f, CallbackInfo ci) {
        // While the BRBE R/U viewer overlay is up, the recipe book stays locked
        // to its current page: consume any queued scroll instead of flipping.
        if (RecipeViewerIndex.isViewerActive()) {
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
                if (currentPage != oldPage
                        && BetterRecipeBook.config.scrollPageSound
                        && Minecraft.getInstance().getSoundManager() != null) {
                    // 0.01s 播放间隔节流，避免快速滚动时音效过密。
                    long now = Util.getMillis();
                    if (now - brbe$lastPageFlipSoundTime >= 10) {
                        brbe$lastPageFlipSoundTime = now;
                        // pageFlipVolume 0.0–1.5，默认 1.0 = 原生音量（playButtonClickSound 用 0.25）。
                        float volume = 0.25f * BetterRecipeBook.config.pageFlipVolume;
                        if (volume > 0.0f) {
                            Minecraft.getInstance().getSoundManager().play(
                                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, volume));
                        }
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

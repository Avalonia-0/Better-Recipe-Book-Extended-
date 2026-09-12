package com.alonie.brbe.mixins.hideoverlay;

import com.alonie.brbe.BetterRecipeBook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "mezz.jei.gui.overlay.IngredientListOverlay", remap = false)
public abstract class IngredientListOverlayMixin {

    private static boolean warned;

    @Inject(method = "drawScreen", at = @At("HEAD"), cancellable = true, remap = false)
    private void brbe$cancelIngredientListOverlay(CallbackInfo ci) {
        // Hidden only while the config toggle is on.  BRBE overlays (query
        // viewer / pins) must NOT hide the real JEI: co-existence is expected
        // and the ingredient list stays interactive beside the viewer.  The
        // reflection-based OverlayHider path is unreliable against real JEI,
        // so this mixin is the authoritative gate for the config toggle.
        if (BetterRecipeBook.config != null
                && BetterRecipeBook.config.hideReiJeiOverlay) {
            if (!warned) {
                warned = true;
                BetterRecipeBook.LOGGER.warn(
                        "[BRBE] JEI ingredient overlay hidden (config toggle)");
            }
            ci.cancel();
        }
    }

    /** JEI「列表是否显示」总闸（2026-09-12 修复）：JEI 30.x 的四个绘制入口
     *  {@code drawBackground/drawForeground/drawTooltips/drawOnForeground} 与 27.x 的
     *  {@code drawScreen} 全部以它为守卫，输入层 {@code GuiEventHandler} 也按它判定命中
     *  —— 让它回 false，绘制全部跳过，同时不会留下"看不见但能点"的透明覆盖层。
     *  （旧实现只拦 {@code drawScreen}：JEI 30.x 已不再调用它 → 开关完全失效。） */
    @Inject(method = "isListDisplayed", at = @At("HEAD"), cancellable = true, remap = false)
    private void brbe$reportIngredientListHidden(CallbackInfoReturnable<Boolean> cir) {
        if (BetterRecipeBook.config != null && BetterRecipeBook.config.hideReiJeiOverlay) {
            if (!warned) {
                warned = true;
                BetterRecipeBook.LOGGER.warn(
                        "[BRBE] JEI ingredient overlay hidden (config toggle)");
            }
            cir.setReturnValue(false);
        }
    }
}

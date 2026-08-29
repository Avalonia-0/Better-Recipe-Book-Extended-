package com.alonie.brbe.mixins.hideoverlay;

import com.alonie.brbe.BetterRecipeBook;
import com.alonie.brbe.util.RecipeViewerOverlay;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A/R/U 的 hideReiJeiOverlay 侧处理（1.21.1 raw (int,int,int) 键事件）。
 *
 * <p>BRBE viewer 的键位/点击/滚轮由 {@code recipeviewer} 包的新 mixin 层处理
 * （KeyboardHandler priority 2000 + AbstractContainerScreen 注入）；本类只保留
 * hideReiJeiOverlay 语义的兜底：A 键防 REI/JEI 收藏、BRBE 引擎打不开时
 * R/U 路由到外部 viewer（ItemViewCompat）。viewer 激活期间全部跳过。</p>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    // GLFW key constant for A (REI/JEI favorites)
    private static final int KEY_A = 65;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void brbe$handleKeysOnHiddenOverlay(int keyCode, int scancode, int modifiers,
                                                CallbackInfoReturnable<Boolean> cir) {
        // BRBE viewer 激活时其键处理已发生（recipeviewer mixin 在 HEAD 先跑）：
        // 这里不再重复；A/R/U 全部留给 viewer/pin 层。
        if (RecipeViewerOverlay.isActive()) {
            return;
        }
        if (!BetterRecipeBook.ctx().config().hideReiJeiOverlay) {
            return;
        }

        // A key: prevent REI/JEI favorites. Skip if text field is focused.
        if (keyCode == KEY_A) {
            Screen screen = (Screen) (Object) this;
            if (!(screen.getFocused() instanceof EditBox)) {
                cir.setReturnValue(true);
            }
            return;
        }
    }
}

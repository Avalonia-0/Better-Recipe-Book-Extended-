package com.alonie.brbe.mixins.recipeviewer;

import com.alonie.brbe.util.CycleLock;
import com.alonie.brbe.util.RecipeViewerOverlay;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 「替代配方组开着时，与 LEI 交互不关闭替代配方组」（用户 2026-09-13）。
 *
 * <p>原版 {@code RecipeBookPage.mouseClicked} 的语义是：只要替代配方组
 * （{@code OverlayRecipeComponent}）可见，这一击就归它——点到组内按钮就选配方，
 * <b>点到组外一律 {@code setVisible(false)} 关掉</b>。而 LEI 浮层（查询窗口 /
 * Shift 预览 / pin）是画在配方书之上的独立层：用户只是在查询窗口里点了空白处，
 * 这一击不该被当成"点到替代配方组之外"——替代配方组会莫名其妙消失。</p>
 *
 * <p>这里**只拦这一个关闭调用**：指针落在 LEI 浮层上时跳过
 * {@code overlay.setVisible(false)}（用当前光标位置判定，点击事件就是按当时的光标
 * 派发的；与滚轮路径用的是同一个 {@link RecipeViewerOverlay#modalMaskOwnsCursor}）。
 * 其余一切保持原版：点到组内按钮、点到配方书其他位置、ESC、翻页、关书都照旧关闭。</p>
 *
 * <p>注意不能改成"吞掉整次点击"：1.21.1 的 LEI 点击处理挂在
 * {@code AbstractContainerScreen.mouseClicked}（在配方书之后才轮到），吞掉点击会让
 * LEI 反而收不到这一击。只拦关闭调用则三个分支行为一致——LEI 照常响应，替代配方组
 * 不陪葬。</p>
 */
@Mixin(RecipeBookPage.class)
public abstract class RecipeBookPageClickMixin {

    @Redirect(method = "mouseClicked", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/recipebook/OverlayRecipeComponent;setVisible(Z)V"))
    private void brbe$keepAltGroupWhileViewerOwnsCursor(OverlayRecipeComponent overlay, boolean visible) {
        if (!visible && RecipeViewerOverlay.modalMaskOwnsCursor(
                CycleLock.cursorX(), CycleLock.cursorY())) {
            return;
        }
        overlay.setVisible(visible);
    }
}

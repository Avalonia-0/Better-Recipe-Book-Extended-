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
 * <p>2026-09-13 补充（同日第二个 bug：替代配方组开着时 LEI 完全无法操作）：这一击在
 * **上层**就已经归 LEI 了——三个分支的 LEI 点击判定都挪到了配方书之前（26.2 / 1.21.11：
 * {@code recipeviewer.AbstractRecipeBookScreenMixin} 的 HEAD 注入器，且
 * {@code pins.AbstractContainerScreenMixin} 现在会让出落在 LEI 浮层上的点击；1.21.1：
 * 新增的 {@code recipeviewer.RecipeBookComponentMixin} 把 LEI 判定提前到配方书组件之前）。
 * 本 redirect 是**兜底**：LEI 没有消费掉、但确实落在 LEI 浮层上的点击（例如只有 pin
 * 浮层、没有查询窗口时）原版仍会把它当作「点到替代配方组之外」——这里跳过那次关闭，
 * 于是替代配方组不陪葬。</p>
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

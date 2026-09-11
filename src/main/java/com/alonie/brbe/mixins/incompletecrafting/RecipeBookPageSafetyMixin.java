package com.alonie.brbe.mixins.incompletecrafting;

import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 「空配方按钮」的收尾守卫 —— {@link RecipeButtonSafetyMixin} 的补完。
 *
 * <p>当某个 {@code RecipeCollection} 在当前 {@code isFiltering} 状态下没有任何可渲染条目时，
 * {@code RecipeButton.init()} 得到的 {@code selectedEntries} 为空，vanilla 的
 * {@code getDisplayStack()} / {@code getCurrentRecipe()} 都会除以 0：</p>
 * <ul>
 *   <li><b>渲染路径</b>：已被 {@code RecipeButtonSafetyMixin} 捕获并返回
 *       {@code ItemStack.EMPTY} → 按钮画成一个**空槽位（空气占位符）**；</li>
 *   <li><b>点击路径</b>：{@code RecipeBookPage.mouseClicked} 直接调用
 *       {@code RecipeButton.getCurrentRecipe()}，**没有兜底** →
 *       {@code java.lang.ArithmeticException: / by zero} → 崩客户端
 *       （2026-09-11 实例日志实锤：mouseClicked event handler → RecipeButton.getCurrentRecipe）。</li>
 * </ul>
 *
 * <p>本 mixin 把这两点都补齐：翻页/刷新后把**没有可渲染条目**的按钮直接隐藏
 * （用户预期是"该被隐藏"，而不是留一个空气占位符），并在点击入口再兜一层，
 * 即使隐藏失效也不会崩。</p>
 *
 * <p>这是**安全网**：正常路径下不应再产生空按钮——根因已修（管线输出缓存键补
 * {@code isFiltering} / 搜索词原文；{@code incompletecrafting/RecipeBookComponentMixin}
 * 的 removeIf 挂点不再绕过 vanilla 的 {@code !hasAnySelected()} 过滤）。</p>
 */
@Mixin(RecipeBookPage.class)
public abstract class RecipeBookPageSafetyMixin {

    @Shadow @Final private List<RecipeButton> buttons;

    /** 按钮没有任何可渲染条目时为 true（vanilla 会在渲染/点击时除以 0）。 */
    @Unique
    private static boolean brbe$isEmptyButton(RecipeButton button) {
        try {
            button.getCurrentRecipe();
            return false;
        } catch (ArithmeticException e) {
            return true;
        }
    }

    /** 翻页/刷新后隐藏空按钮（vanilla 在 {@code init} 之后统一置 {@code visible = true}）。 */
    @Inject(method = "updateButtonsForPage", at = @At("RETURN"))
    private void brbe$hideEmptyButtons(CallbackInfo ci) {
        for (RecipeButton button : this.buttons) {
            if (button.visible && brbe$isEmptyButton(button)) {
                button.visible = false;
            }
        }
    }

    /**
     * 点到空按钮时吞掉这次点击（返回 false = 未被消费），避免
     * {@code getCurrentRecipe()} 的除以 0 把客户端打崩。
     */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void brbe$ignoreClickOnEmptyButton(MouseButtonEvent event, int xo, int yo,
                                               int imageWidth, int imageHeight, boolean doubleClick,
                                               CallbackInfoReturnable<Boolean> cir) {
        for (RecipeButton button : this.buttons) {
            if (!button.visible || !button.isMouseOver(event.x(), event.y())) continue;
            if (brbe$isEmptyButton(button)) {
                cir.setReturnValue(false);
            }
            return;
        }
    }
}

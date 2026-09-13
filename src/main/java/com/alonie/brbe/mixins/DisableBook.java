package com.alonie.brbe.mixins;

import com.alonie.brbe.BetterRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 「启用配方书」关闭时让原版配方书组件**彻底停摆**。
 *
 * <p>⚠️ 必须**同时**压住两个可见性来源：{@code isVisible()}（对外的可见性）与
 * {@code isVisibleAccordingToBookData()}（按 {@code ClientRecipeBook} 的"这本书是开的"
 * 记录算出来的可见性）。原版 {@code tick()} 每 tick 做：</p>
 *
 * <pre>
 * boolean byBookData = isVisibleAccordingToBookData();
 * if (isVisible() != byBookData) setVisible(byBookData);   // ← 不一致就同步
 * </pre>
 *
 * <p>而原版 {@code setVisible(true)} 的第一件事是 **{@code initVisuals()}**——整块标签/网格
 * 重建。只覆盖 {@code isVisible()} 时：本模组恒返回 false、书数据说 true → **每 tick 都
 * "不一致"** → 每 tick {@code setVisible(true)} → **每 tick initVisuals()** → 连带
 * {@code updateTabs()} → RBIP 的 Polymer 兼容刷新（遍历**全部注册物品**重建命名空间缓存
 * 并重新登记所有创造标签）。实测症状（用户 2026-09-13）：关闭「启用配方书」后打开任何
 * 原本带配方书的功能方块界面，帧率被拖垮，日志里 {@code [RBIP] Namespace override} 每分钟
 * 2400 行。两侧一起返回 false 后 tick 的比较恒等，{@code setVisible}/{@code initVisuals}
 * 不再触发。</p>
 */
@Mixin(RecipeBookComponent.class)
public class DisableBook {

    @Inject(at = @At("HEAD"), method = "isVisible", cancellable = true)
    public void isOpen(CallbackInfoReturnable<Boolean> cir) {
        if (BetterRecipeBook.config != null && !BetterRecipeBook.config.enableBook) {
            cir.setReturnValue(false);
        }
    }

    /** @see DisableBook 类注释：这一侧不改就会每 tick 触发 setVisible→initVisuals。 */
    @Inject(at = @At("HEAD"), method = "isVisibleAccordingToBookData", cancellable = true)
    public void isOpenAccordingToBookData(CallbackInfoReturnable<Boolean> cir) {
        if (BetterRecipeBook.config != null && !BetterRecipeBook.config.enableBook) {
            cir.setReturnValue(false);
        }
    }
}

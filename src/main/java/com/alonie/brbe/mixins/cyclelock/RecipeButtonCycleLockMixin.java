package com.alonie.brbe.mixins.cyclelock;

import com.alonie.brbe.util.CycleLock;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 配方书**网格按钮**的逐物品折叠锁（用户 2026-09-13 诉求 2）。
 *
 * <p>原版配方书把**同一个** {@code SlotSelectTime} 交给每个
 * {@code RecipeButton}，取值点就在这两个方法里（{@code getDisplayStack} 选显示
 * 的变体、{@code getCurrentRecipe} 选点击放置的配方）。这里在取值前后把"正在
 * 绘制的是这一个按钮"压进 {@link CycleLock} 的绘制上下文——共享的
 * {@code SlotSelectTime}（{@link com.alonie.brbe.util.CycleLockSlotSelectTime}）
 * 便在 {@code CycleLock.resolveContext} 里就地判断：只有指针下这一个按钮返回
 * 冻结下标，其余按钮照常自动轮换。</p>
 *
 * <p>⚠️ 这两个目标方法**有返回值**，所以回调参数必须是
 * {@link CallbackInfoReturnable}（用 {@code CallbackInfo} 会在类变换时抛
 * {@code InvalidInjectionException: CallbackInfoReturnable is required}——
 * 2026-09-13 进存档闪退即此因）。</p>
 *
 * <p>按钮整块当作**一件**折叠物品（矩形取 widget 自身的屏幕矩形）；面板里的
 * 槽位另有逐槽位处理（见 {@code PopupRenderer}）。</p>
 */
@Mixin(RecipeButton.class)
public abstract class RecipeButtonCycleLockMixin {

    @Inject(method = "getDisplayStack", at = @At("HEAD"))
    private void brbe$pushDisplayContext(CallbackInfoReturnable<ItemStack> cir) {
        brbe$push();
    }

    @Inject(method = "getDisplayStack", at = @At("RETURN"))
    private void brbe$popDisplayContext(CallbackInfoReturnable<ItemStack> cir) {
        CycleLock.popContext();
    }

    @Inject(method = "getCurrentRecipe", at = @At("HEAD"))
    private void brbe$pushRecipeContext(CallbackInfoReturnable<RecipeDisplayId> cir) {
        brbe$push();
    }

    @Inject(method = "getCurrentRecipe", at = @At("RETURN"))
    private void brbe$popRecipeContext(CallbackInfoReturnable<RecipeDisplayId> cir) {
        CycleLock.popContext();
    }

    private void brbe$push() {
        AbstractWidget self = (AbstractWidget) (Object) this;
        CycleLock.pushContext(self, self.getX(), self.getY(), self.getWidth(), self.getHeight());
    }
}

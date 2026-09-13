package com.alonie.brbe.mixins.cyclelock;

import com.alonie.brbe.util.CycleLock;
import net.minecraft.client.gui.screens.recipebook.GhostRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 功能方块里**幽灵物品**的逐物品折叠锁（1.21.1 专有挂钩，用户 2026-09-13 诉求 2）。
 *
 * <p>1.21.1 的幽灵物品变体由所属 {@code GhostRecipe.time} 驱动
 * （{@code getItem() = items[floor(time / 30) % n]}），而一件幽灵物品就是**一个**
 * {@code GhostIngredient}（自己的位置 + 自己的候选物品表）——所以逐物品锁定落在
 * 这个方法上：</p>
 *
 * <ul>
 *   <li>指针是否落在这件幽灵物品上：屏幕矩形 = 渲染原点（
 *       {@link GhostRecipeOrigin}，由 {@link GhostRecipeCycleLockMixin} 记录）+
 *       本实例的容器相对 {@code x/y}，16x16；LEI 浮层挡住指针时不判定
 *       （{@code CycleLock.claimScreen}）。</li>
 *   <li>被指着 → 把变体下标交给 {@link CycleLock}（首次冻结 latch 住**当时显示**
 *       的那一个变体——即原版刚算出来的返回值在候选表里的位置），锁定键+滚轮逐格
 *       翻动它；没被指着 → 原样返回原版的自动轮换结果。</li>
 * </ul>
 */
@Mixin(targets = "net.minecraft.client.gui.screens.recipebook.GhostRecipe$GhostIngredient")
public abstract class GhostIngredientCycleLockMixin {

    @Shadow
    @Final
    private Ingredient ingredient;

    /** 所属幽灵配方（记录渲染原点用）。 */
    @Shadow
    @Final
    GhostRecipe field_3085;

    @Shadow
    public abstract int getX();

    @Shadow
    public abstract int getY();

    @Inject(method = "getItem", at = @At("RETURN"), cancellable = true)
    private void brbe$lockedVariant(CallbackInfoReturnable<ItemStack> cir) {
        ItemStack[] items = this.ingredient.getItems();
        if (items.length <= 1) return; // 单变体不是折叠物品
        GhostRecipe outer = this.field_3085;
        if (!(outer instanceof GhostRecipeOrigin origin)) return;
        Object key = this;
        if (!CycleLock.claimScreen(key, origin.brbe$originX() + getX(),
                origin.brbe$originY() + getY(), 16, 16)) {
            CycleLock.release(key);
            return;
        }
        // 原版刚算出的变体就是用户此刻看到的那一个 —— 按它的值定位下标后交给
        // CycleLock（首次冻结就是「冻结住当下看到的那一个」）。
        ItemStack shown = cir.getReturnValue();
        int auto = 0;
        for (int i = 0; i < items.length; i++) {
            if (ItemStack.isSameItemSameComponents(items[i], shown)) {
                auto = i;
                break;
            }
        }
        int idx = CycleLock.indexFor(key, auto);
        cir.setReturnValue(items[Math.floorMod(idx, items.length)]);
    }
}

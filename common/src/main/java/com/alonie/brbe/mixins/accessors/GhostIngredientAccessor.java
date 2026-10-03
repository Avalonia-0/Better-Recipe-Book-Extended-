package com.alonie.brbe.mixins.accessors;

import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读 1.21.1 幽灵条目的**原料**（{@code GhostRecipe$GhostIngredient} 只暴露
 * {@code getItem()}——按 {@code time} 轮循出的**单个**展示物品，判定"这格够不够"必须拿到
 * 整个 {@link Ingredient} 的候选列表）。
 *
 * <p>用途：{@code HoverGhostRecipe} 的「工作区已经摆好这条配方 → 不预览」判定
 * （用户 2026-09-27 收尾诉求）。</p>
 */
@Mixin(targets = "net.minecraft.client.gui.screens.recipebook.GhostRecipe$GhostIngredient")
public interface GhostIngredientAccessor {

    @Accessor("ingredient")
    Ingredient brbe$getIngredient();
}
